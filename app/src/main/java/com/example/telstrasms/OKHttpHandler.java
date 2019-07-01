package com.example.telstrasms;

import android.os.AsyncTask;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class OKHttpHandler extends AsyncTask<Request, Void, Response> {
    private OkHttpClient client = new OkHttpClient();

    @Override
    protected Response doInBackground(Request... requests) {
        try {
            return client.newCall(requests[0]).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
