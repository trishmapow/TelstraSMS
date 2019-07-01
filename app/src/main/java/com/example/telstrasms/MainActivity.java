package com.example.telstrasms;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private OkHttpClient client = new OkHttpClient();

    private void getNumberStatus(String bearer) {
        final TextView infoText = findViewById(R.id.infoText);

        Request request = new Request.Builder()
                .url("https://tapi.telstra.com/v2/messages/provisioning/subscriptions")
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .addHeader("authorization", String.format("Bearer %s", bearer))
                .addHeader("cache-control", "no-cache")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                MainActivity.this.runOnUiThread(() -> infoText.setText(getString(R.string.status_err_io)));
            }

            @Override
            public void onResponse(Call call, Response response) {
                MainActivity.this.runOnUiThread(() -> {
                    try {
                        String json = response.body().string();
                        JSONObject jObject = new JSONObject(json);
                        String number = jObject.getString("destinationAddress");
                        String expiry = jObject.getString("activeDays");
                        infoText.setText(String.format("%s exp. in %s days", number, expiry));

                        // Auto renew if expiry < 5 days
                        if (Integer.parseInt(expiry) < 5)
                            extendNumber(bearer);
                    } catch (IOException e) {
                        infoText.setText(getString(R.string.status_err));
                    } catch (JSONException e) {
                        infoText.setText(getString(R.string.status_json_err));
                        extendNumber(bearer);
                    }
                });
            }
        });
    }

    private void extendNumber(String bearer) {
        final TextView infoText = findViewById(R.id.infoText);

        RequestBody body = new FormBody.Builder().build();
        Request request = new Request.Builder()
                .url("https://tapi.telstra.com/v2/messages/provisioning/subscriptions")
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/x-www-form-urlencoded")
                .addHeader("authorization", String.format("Bearer %s", bearer))
                .addHeader("cache-control", "no-cache")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                MainActivity.this.runOnUiThread(() -> infoText.setText(String.format("%s %s", infoText.getText(), getString(R.string.rnw_fail))));
            }

            @Override
            public void onResponse(Call call, Response response) {
                String status;
                if (response.code() == 201) {
                    status = getString(R.string.rnw_succ);
                } else {
                    status = getString(R.string.rnw_fail);
                }
                MainActivity.this.runOnUiThread(() -> infoText.setText(String.format("%s %s", infoText.getText(), status)));
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences sharedPrefDef = PreferenceManager.getDefaultSharedPreferences(this);

        final Button button = findViewById(R.id.authButton);
        final TextView debugText = findViewById(R.id.debugText);
        final TextView bearerText = findViewById(R.id.bearerText);
        final EditText phoneInput = findViewById(R.id.phoneInput);
        final EditText msgInput = findViewById(R.id.messageInput);
        final Button sendButton = findViewById(R.id.sendButton);
        final Button getButton = findViewById(R.id.getButton);

        button.setOnClickListener(v -> {
            String keySecret = sharedPrefDef.getString("api_key", "");
            if (keySecret == null || keySecret.length() < 49) return;

            String[] keySecretSplit = keySecret.split(" ");
            String key = keySecretSplit[0];
            String secret = keySecretSplit[1];

            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", key)
                    .add("client_secret", secret)
                    .build();
            Request request = new Request.Builder()
                    .url("https://tapi.telstra.com/v2/oauth/token")
                    .addHeader("content-type", "application/x-www-form-urlencoded")
                    .addHeader("cache-control", "no-cache")
                    .post(formBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    MainActivity.this.runOnUiThread(() -> bearerText.setText(getString(R.string.bearer_error)));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    MainActivity.this.runOnUiThread(() -> {
                        String bearer = null;
                        try {
                            String json = response.body().string();
                            JSONObject jObject = new JSONObject(json);
                            bearer = jObject.getString("access_token");
                            bearerText.setText(bearer);
                            sharedPref.edit().putString("BEARER", bearer).apply();
                        } catch (IOException e) {
                            bearerText.setText(String.format("Got %s response", response.code()));
                        } catch (JSONException e) {
                            bearerText.setText(getString(R.string.json_error));
                        }
                        if (bearer != null)
                            getNumberStatus(bearer);
                    });
                }
            });
        });

        sendButton.setOnClickListener(v -> {
            if ("".equals(sharedPref.getString("BEARER", ""))) {
                debugText.setText(this.getString(R.string.bearer_missing));
                return;
            }

            String number = phoneInput.getText().toString();
            String message = msgInput.getText().toString();

            JSONObject j = new JSONObject();
            try {
                j.put("to", number);
                j.put("body", message);

                int validity = Integer.parseInt(sharedPrefDef.getString("validity", ""));
                if (validity > 0 && validity < 7*24*60)
                    j.put("validity", validity);
                else
                    Toast.makeText(this, "Invalid validity (<0 or >= 7 days)", Toast.LENGTH_SHORT).show();

                int wait = Integer.parseInt(sharedPrefDef.getString("wait", ""));
                if (wait > 0)
                    j.put("scheduledDelivery", wait);
                else
                    Toast.makeText(this, "Invalid wait time (<0 min)", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                debugText.setText(this.getString(R.string.json_err));
            }
            RequestBody formBody = RequestBody.create(JSON, j.toString());
            Request request = new Request.Builder()
                    .url("https://tapi.telstra.com/v2/messages/sms")
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", String.format("Bearer %s", sharedPref.getString("BEARER", "")))
                    .addHeader("cache-control", "no-cache")
                    .post(formBody)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    MainActivity.this.runOnUiThread(() -> debugText.setText(getString(R.string.send_fail)));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    MainActivity.this.runOnUiThread(() -> {
                        try {
                            debugText.setText(response.body().string());
                            msgInput.setText("");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            });
        });

        getButton.setOnClickListener(v -> {
            if ("".equals(sharedPref.getString("BEARER", ""))) {
                debugText.setText(this.getString(R.string.bearer_missing));
                return;
            }

            Request request = new Request.Builder()
                    .url("https://tapi.telstra.com/v2/messages/sms")
                    .addHeader("accept", "application/json")
                    .addHeader("content-type", "application/json")
                    .addHeader("authorization", String.format("Bearer %s", sharedPref.getString("BEARER", "")))
                    .addHeader("cache-control", "no-cache")
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    MainActivity.this.runOnUiThread(() -> debugText.setText(getString(R.string.get_fail)));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    MainActivity.this.runOnUiThread(() -> {
                        try {
                            String json = response.body().string();
                            JSONObject jObject = new JSONObject(json);
                            if ("EMPTY".equals(jObject.getString("status"))) {
                                debugText.setText(getString(R.string.no_msgs));
                            } else {
                                String sender = jObject.getString("senderAddress");
                                String time = jObject.getString("sentTimestamp");
                                String message = jObject.getString("message");
                                debugText.setText(String.format("%s @ %s: %s", sender, time, message));
                            }
                        } catch (IOException e) {
                            debugText.setText(String.format("Got %s response", response.code()));
                        } catch (JSONException e) {
                            debugText.setText(getString(R.string.json_error));
                        }
                    });
                }
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_about:
                Toast.makeText(this, "TelstraSMS v0.1 by trishmapow", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
        return true;
    }
}
