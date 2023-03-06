package edu.ucsd.cse110.sharednotes.model;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import com.google.gson.Gson;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NoteRepository {
    private final NoteDao dao;
    private ScheduledFuture<?> poller; // what could this be for... hmm?

    public NoteRepository(NoteDao dao) {
        this.dao = dao;
    }

    // Synced Methodsgi
    // ==============

    /**
     * This is where the magic happens. This method will return a LiveData object that will be
     * updated when the note is updated either locally or remotely on the server. Our activities
     * however will only need to observe this one LiveData object, and don't need to care where
     * it comes from!
     * <p>
     * This method will always prefer the newest version of the note.
     *
     * @param title the title of the note
     * @return a LiveData object that will be updated when the note is updated locally or remotely.
     */
    public LiveData<Note> getSynced(String title) {
        var note = new MediatorLiveData<Note>();

        Observer<Note> updateFromRemote = theirNote -> {
            var ourNote = note.getValue();
            if (theirNote == null) return; // do nothing
            if (ourNote == null || ourNote.version < theirNote.version) {
                upsertLocal(theirNote, false);
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

    public void upsertLocal(Note note, boolean incrementVersion) {
        // We don't want to increment when we sync from the server, just when we save.
        if (incrementVersion) note.version = note.version + 1;
        note.version = note.version + 1;
        dao.upsert(note);
    }

    public void upsertLocal(Note note) {
        upsertLocal(note, true);
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

        // Cancel any previous poller if it exists.
        if (this.poller != null && !this.poller.isCancelled()) {
            poller.cancel(true);
        }

        // Set up a background thread that will poll the server every 3 seconds.

        // You may (but don't have to) want to cache the LiveData's for each title, so that
        // you don't create a new polling thread every time you call getRemote with the same title.
        // You don't need to worry about killing background threads.

        // TO TEST: make sure it updates a note from the server every 3 seconds
        //do this first here, get remote data from server into some variable, then refresh every 3 sec, maybe make this a method in noteAPI
        NoteAPI noteAPI = new NoteAPI();
        var executor = Executors.newSingleThreadScheduledExecutor();

        MutableLiveData<Note> fromRemote = new MutableLiveData<>();
        //var future =
        executor.submit(() -> fromRemote.setValue(noteAPI.pullFromRemote(title).getValue()));
            //copied this from lab 4 threading i guess
            //here it says you need to not be on main thread

        //unsure how to do the scheduledexecutorservice thread that polls every 3 seconds
        final MediatorLiveData<Note> ans = new MediatorLiveData<>();
        ans.addSource(fromRemote, ans::postValue);//not sure what to do here to add data source

        ScheduledFuture<?> clockFuture = executor.scheduleAtFixedRate(() -> {//sus
            fromRemote.postValue(noteAPI.pullFromRemote(title).getValue());//this should set the liveData for the answer ans??, maybe need to swap variables
//says cannot invoke setValue on a background thread
        }, 0, 3000, TimeUnit.MILLISECONDS);

        return ans;
        //throw new NotImplementedError();
    }

    public void upsertRemote(Note note) {

        // TODO: Implement upsertRemote!

        final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient();

        //mess with this until I can create API object

        //TO CREATE JSON OBJECT: do the following
        String url = "https://sharednotes.goto.ucsd.edu/notes/"; //then also add title to the end?
        url = url + note.title;
        JSONObject jsonObject = new JSONObject();
        //Inserting key-value pairs into the json object
        try {
            jsonObject.put("title", note.title);
            jsonObject.put("content", note.content);
            jsonObject.put("version", Long.toString(note.version)); //unsure if u need to change this;
        } catch(Exception e){
            throw new RuntimeException(e);
        }
        String json = jsonObject.toString(); //no idea how to read this as string, or if we need to do this as string

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .build();


        ExecutorService executor = Executors.newSingleThreadExecutor();//run this thing on different thread
        //Future future;
        //future =
        executor.submit(() -> {
            try{ //this makes the httpclient execute the request?
                Response r = client.newCall(request).execute();
                Log.i("UPSERT", request.toString());
                Log.i("UPSERT_BODY", body.toString());
            } catch(Exception e){
                throw new RuntimeException(e);
            }
        });
    }
}
