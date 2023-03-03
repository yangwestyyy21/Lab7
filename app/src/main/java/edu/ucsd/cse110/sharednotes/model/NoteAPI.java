package edu.ucsd.cse110.sharednotes.model;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class NoteAPI {
    // TODO: Implement the API using OkHttp!
    // TODO: - getNote (maybe getNoteAsync)
    // TODO: - putNote (don't need putNotAsync, probably)
    // TODO: Read the docs: https://square.github.io/okhttp/
    // TODO: Read the docs: https://sharednotes.goto.ucsd.edu/docs

    private volatile static NoteAPI instance = null;

    private OkHttpClient client;

    public NoteAPI() {
        this.client = new OkHttpClient();
    }

    public static NoteAPI provide() {
        if (instance == null) {
            instance = new NoteAPI();
        }
        return instance;
    }

    public MutableLiveData<Note> pullFromRemote(String title) {
        MutableLiveData ans = new MutableLiveData();
        String msg = title.replace(" ", "%20"); //ig this is the input string or name of note?
        var request = new Request.Builder()
                .url("https://sharednotes.goto.ucsd.edu/docs" + msg)
                .method("GET", null)
                .build();
        try(var response = client.newCall(request).execute()) {
            var body = response.body().string();
            Note toAdd = Note.fromJSON(body); //make a new note to add with the title and whatever was retrieved?
            ans.postValue(toAdd); //add this to our mutablelivedata
        } catch(Exception e) {
            e.printStackTrace();
        } //after this maybe we need to update local?
        return ans;
    }

    /**
     * An example of sending a GET request to the server.
     *
     * The /echo/{msg} endpoint always just returns {"message": msg}.
     *
     * This method should can be called on a background thread (Android
     * disallows network requests on the main thread).
     */
    @WorkerThread
    public String echo(String msg) {
        // URLs cannot contain spaces, so we replace them with %20.
        String encodedMsg = msg.replace(" ", "%20");

        var request = new Request.Builder()
                .url("https://sharednotes.goto.ucsd.edu/echo/" + encodedMsg)
                .method("GET", null)
                .build();

        try (var response = client.newCall(request).execute()) {
            assert response.body() != null;
            var body = response.body().string();
            Log.i("ECHO", body);
            return body;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @AnyThread
    public Future<String> echoAsync(String msg) {
        var executor = Executors.newSingleThreadExecutor();
        var future = executor.submit(() -> echo(msg));

        // We can use future.get(1, SECONDS) to wait for the result.
        return future;
    }
}
