package edu.ucsd.cse110.sharednotes.model;

import android.os.FileUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import com.google.gson.Gson;

import org.json.JSONObject;

import kotlin.NotImplementedError;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class NoteRepository {
    private final NoteDao dao;

    public NoteRepository(NoteDao dao) {
        this.dao = dao;
    }

    // Synced Methods
    // ==============

    /**
     * This is where the magic happens. This method will return a LiveData object that will be
     * updated when the note is updated either locally or remotely on the server. Our activities
     * however will only need to observe this one LiveData object, and don't need to care where
     * it comes from!
     *
     * This method will always prefer the newest version of the note.
     *
     * @param title the title of the note
     * @return a LiveData object that will be updated when the note is updated locally or remotely.
     */
    public LiveData<Note> getSynced(String title) {
        var note = new MediatorLiveData<Note>();

        Observer<Note> updateFromRemote = theirNote -> {
            var ourNote = note.getValue();
            if (ourNote == null || ourNote.updatedAt < theirNote.updatedAt) {
                upsertLocal(theirNote);
            }
        };

        // If we get a local update, pass it on.
        note.addSource(getLocal(title), note::postValue);
        // If we get a remote update, update the local version (triggering the above observer)
        note.addSource(getRemote(title), updateFromRemote);

        return note;
    }

    public void upsertSynced(Note note) {
        upsertLocal(note);
        upsertRemote(note);
    }

    // Local Methods
    // =============

    public LiveData<Note> getLocal(String title) {
        return dao.get(title);
    }

    public LiveData<List<Note>> getAllLocal() {
        return dao.getAll();
    }

    public void upsertLocal(Note note) {
        note.updatedAt = System.currentTimeMillis();
        dao.upsert(note);
    }

    public void deleteLocal(Note note) {
        dao.delete(note);
    }

    public boolean existsLocal(String title) {
        return dao.exists(title);
    }

    // Remote Methods
    // ==============

    public LiveData<Note> getRemote(String title) {
        // TODO: Implement getRemote!
        // TODO: Set up polling background thread (MutableLiveData?)
        // TODO: Refer to TimerService from https://github.com/DylanLukes/CSE-110-WI23-Demo5-V2.

        // Start by fetching the note from the server _once_ and feeding it into MutableLiveData.
        // Then, set up a background thread that will poll the server every 3 seconds.

        // You may (but don't have to) want to cache the LiveData's for each title, so that
        // you don't create a new polling thread every time you call getRemote with the same title.
        // You don't need to worry about killing background threads.

        // TO TEST: make sure it updates a note from the server every 3 seconds
        //do this first here, get remote data from server into some variable, then refresh every 3 sec, maybe make this a method in noteAPI
        NoteAPI noteAPI = new NoteAPI();
        MutableLiveData<Note> fromRemote = noteAPI.pullFromRemote(title);

        //unsure how to do the scheduledexecutorservice thread that polls every 3 seconds
        ScheduledFuture<?> clockFuture;
        final MediatorLiveData<Note> ans = new MediatorLiveData<>();
        ans.addSource(ans, fromRemote::postValue);//not sure what to do here to add data source
        var executor = Executors.newSingleThreadScheduledExecutor();
        clockFuture = executor.scheduleAtFixedRate(() -> {
            MutableLiveData<Note> newFromRemote = noteAPI.pullFromRemote(title);//sus
            ans.postValue(newFromRemote.getValue());//this should set the liveData for the answer ans??, maybe need to swap variables

        }, 0, 3000, TimeUnit.MILLISECONDS);

        return ans;
        //throw new NotImplementedError();
    }

    public void upsertRemote(Note note) {

        // TODO: Implement upsertRemote!

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient();

        //mess with this until I can create API object

        //TO CREATE JSON OBJECT: do the following
        String url = "https://sharednotes.goto.ucsd.edu/"; //then also add title to the end?
        url = url + note.title;

        String json = ""; //title, content, updatedat
        JSONObject jsonObject = new JSONObject();
        //Inserting key-value pairs into the json object
        try {
            jsonObject.put("title", note.title);
            jsonObject.put("content", note.content);
            jsonObject.put("updatedAt", Long.toString(note.updatedAt)); //unsure if u need to change this;
        } catch(Exception e){
            throw new RuntimeException(e);
        }
        json = jsonObject.toString(); //no idea how to read this as string, or if we need to do this as string

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try{ //this makes the httpclient execute the request?
            client.newCall(request).execute();
        } catch(Exception e){
            throw new RuntimeException(e);
        }
        //throw new NotImplementedError();
    }
}
