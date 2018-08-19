/*
 * Copyright (C) 2018 José Almeida <jose.afga@gmail.com>
 *
 * https://creativecommons.org/licenses/by-nc/4.0/
 */

package br.com.joseafga.wiim;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Objects;

import br.com.joseafga.wiim.models.Process;
import br.com.joseafga.wiim.models.Tag;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResultActivity extends AppCompatActivity {

    public RecyclerView mRecyclerView;
    protected TagAdapter mTagAdapter;
    protected CollapsingToolbarLayout mCollapsingToolbar;
    protected ProgressBar mProgressBar;
    // preferences
    protected String apiUrl;
    protected Integer updateInterval;
    // QRCode result (process|tag, id)
    protected String[] qrData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set layout view
        setContentView(R.layout.activity_result);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // add back arrow to toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // set widgets
        mProgressBar = findViewById(R.id.loading_spinner);
        mCollapsingToolbar = findViewById(R.id.collapsing_toolbar);
        mRecyclerView = findViewById(R.id.recycler_view);
        // set recycle view (and layout manager)
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(llm);
        mTagAdapter = new TagAdapter(this, new ArrayList<Tag>()); // begin with empty array to avoid error
        mRecyclerView.setAdapter(mTagAdapter);

        // get intent extras from main activity
        qrData = getIntent().getExtras().getStringArray("QRData");

        getData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.ac_settings:
                Intent settings = new Intent(this, SettingsActivity.class);
                startActivity(settings);

                break;
            // ?more options
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Set Collapsing toolbar texts
     *
     * @param title toolbar text title
     * @param comment toolbar text summary
     * @param zone toolbar bottom text
     */
    public void setToolbarTexts(String title, String comment, String zone) {
        // set process title
        mCollapsingToolbar.setTitle(title);

        // get resources layout
        TextView processSummary = findViewById(R.id.process_summary);
        TextView processZone = findViewById(R.id.process_zone);
        // set values
        processSummary.setText(comment);
        processZone.setText(zone);
    }

    public void updateDelayed(ArrayList<Tag> tagsList) {
        final Handler handler = new Handler();

        // All done, remove progress bar
        mProgressBar.setVisibility(View.GONE);

        // Update TAGs
        mTagAdapter.updateList(tagsList);

        handler.postDelayed(new Runnable() {
            public void run() {
                try {
                    // get data to update process
                    getData();
                } catch (Exception e) {
                    // alert dialog if error occurs
                    onConnectionError(e.getMessage());
                }
            }
        }, updateInterval);
    }

    public void getData() {
        // Update preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        apiUrl = prefs.getString(SettingsActivity.KEY_PREF_SERVER_ADDRESS, "");
        updateInterval = prefs.getInt(SettingsActivity.KEY_PREF_UPDATE_INTERVAL, 0) * 100; // multiply x100 to get real milliseconds

        // API calls
        if (qrData[0].equals("process")) {
            WiimApi.getService(apiUrl).getProcess(qrData[1]).enqueue(new Callback<Process>() {
                @Override
                public void onResponse(Call<Process> call, Response<Process> response) {
                    Process process = response.body();

                    setToolbarTexts(process.getName(), process.getComment(), process.getZone());
                    // recall
                    updateDelayed(process.getTags());
                }

                @Override
                public void onFailure(Call<Process> call, Throwable t) {
                    onConnectionError(t.getMessage());
                }
            });

        } else {
            WiimApi.getService(apiUrl).getTags(qrData[1]).enqueue(new Callback<Tag>() {
                @Override
                public void onResponse(Call<Tag> call, Response<Tag> response) {
                    Tag tag = response.body();

                    setToolbarTexts(tag.getAlias(), tag.getComment(), tag.getName());

                    // put tag in a array to adapter
                    ArrayList<Tag> tagsList = new ArrayList<Tag>();
                    tagsList.add(tag);
                    // recall
                    updateDelayed(tagsList);
                }

                @Override
                public void onFailure(Call<Tag> call, Throwable t) {
                    onConnectionError(t.getMessage());
                }
            });
        }
    }

    /**
     * Show connection errors alert dialog with message
     * It have two buttons to exit application or reconfigure
     *
     * @param msg message text
     */
    private void onConnectionError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(msg)
                .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                        System.exit(0);
                    }
                })
                .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(ResultActivity.this, SettingsActivity.class);
                        startActivity(intent);
                    }
                })
                .create()
                .show();
    }

}
