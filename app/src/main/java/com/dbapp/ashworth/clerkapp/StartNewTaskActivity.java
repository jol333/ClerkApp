package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class StartNewTaskActivity extends AppCompatActivity {
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    File mAudioFile;
    boolean isRecording = false;
    private EditText taskName;
    private Chronometer myChronometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_new_task);

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Record audio");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        final Button nextButton = (Button) findViewById(R.id.next_button_1);
        final Button recordButton = (Button) findViewById(R.id.record_button);
        final Button stopButton = (Button) findViewById(R.id.stop_button);
        final Button retryButton = (Button) findViewById(R.id.retry_button);
        final Button playButton = (Button) findViewById(R.id.play_button);
        taskName = (EditText) findViewById(R.id.task_name);
        myChronometer = (Chronometer) findViewById(R.id.chronometer);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (taskName.getText().toString().trim().isEmpty()) {
                    taskName.setText("");
                    Toast.makeText(getApplicationContext(), "Please enter a valid task name to start recording.", Toast.LENGTH_LONG).show();
                } else if (taskName.getText().toString().trim().length() < 4) {
                    Toast.makeText(getApplicationContext(), "Task name must be minimum 4 characters long.", Toast.LENGTH_LONG).show();
                } else {
                    taskName.setText(taskName.getText().toString().trim().replaceAll(" +", " "));
                    File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp/"+taskName.getText());
                    mydir.mkdirs();
                    requestAudioPermissions();
                    recordButton.setVisibility(View.GONE);
                    stopButton.setVisibility(View.VISIBLE);
                    taskName.setVisibility(View.GONE);
                    myChronometer.setVisibility(View.VISIBLE);
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                stopRecording();
                stopButton.setVisibility(View.GONE);
                retryButton.setVisibility(View.VISIBLE);
                playButton.setVisibility(View.VISIBLE);

                nextButton.setClickable(true);
                nextButton.setEnabled(true);

                //taskName.setText(mPath+"/"+taskName.getText()+"/"+mAudioFile.getName());
                //taskName.setText(mAudioFile.getAbsolutePath());
            }
        });

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(StartNewTaskActivity.this);
                builder
                        .setTitle("Re-record Audio?")
                        .setMessage("Do you really want to record the audio again? Your previous recording will be lost.")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mPlayer != null) {
                                    //mPlayer.stop();
                                    mPlayer.release();
                                    playButton.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_play_arrow_black_24dp), null, null);
                                    playButton.setText("Play");
                                }
                                retryButton.setVisibility(View.GONE);
                                playButton.setVisibility(View.GONE);
                                recordButton.setVisibility(View.VISIBLE);
                                myChronometer.setVisibility(View.GONE);
                                taskName.setVisibility(View.VISIBLE);

                                nextButton.setAlpha(0.25f);
                                nextButton.setClickable(false);
                            }
                        })
                        .setNegativeButton("No", null)                        //Do nothing on no
                        .show();
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playButton.getText().equals("Play")) {
                    playButton.setText("Wait");
                    try {
                        mPlayer = new MediaPlayer();
                        mPlayer.setDataSource(mAudioFile.getAbsolutePath());
                        mPlayer.prepareAsync();

                        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            @Override
                            public void onPrepared(MediaPlayer mp) {
                                playButton.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_stop_black_24dp), null, null);
                                playButton.setText("Stop");
                                mPlayer.start();
                                myChronometer.setBase(SystemClock.elapsedRealtime());
                                myChronometer.stop();
                                myChronometer.start();
                            }
                        });

                        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                playButton.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_play_arrow_black_24dp), null, null);
                                playButton.setText("Play");
                                mPlayer.release();
                                myChronometer.stop();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(e.getMessage(), e.toString());
                    }
                } else {
                    if (mPlayer.isPlaying()) {
                        mPlayer.stop();
                        mPlayer.release();
                        myChronometer.stop();
                    }
                    playButton.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_play_arrow_black_24dp), null, null);
                    playButton.setText("Play");
                }
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StartNewTaskActivity.this, AddPhotosActivity.class);
                i.putExtra("taskPath",Environment.getExternalStorageDirectory() + "/ClerkApp/"+taskName.getText());
                StartNewTaskActivity.this.startActivity(i);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int actionCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (actionCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    startRecording();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            startRecording();
        }
    }

    private boolean startRecording() {
        Toast.makeText(getApplicationContext(), "Recording started.", Toast.LENGTH_SHORT).show();
        try {
            File storageDir = getApplicationContext().getCacheDir();
            //mAudioFile = File.createTempFile(taskName.getText().toString(), ".aac", storageDir);
            String f = Environment.getExternalStorageDirectory() + "/ClerkApp/"+
                    taskName.getText()+"/"+taskName.getText()+".aac";
            mAudioFile = new File(f);
            if (mAudioFile.exists()) mAudioFile.delete();
            mAudioFile.createNewFile();
            if (mRecorder == null) {
                mRecorder = new MediaRecorder();
                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                mRecorder.setOutputFile(mAudioFile.getAbsolutePath());
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            }

            if (!isRecording) {
                try {
                    mRecorder.prepare();
                    mRecorder.start();
                    myChronometer.setBase(SystemClock.elapsedRealtime());
                    myChronometer.stop();
                    myChronometer.start();
                    isRecording = true;
                } catch (IOException e) {
                    Log.e("Audio", "prepare() failed");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(e.getMessage(), e.toString());
        }
        return true;
    }

    private boolean stopRecording() {
        if (isRecording) {
            isRecording = false;
            if (mRecorder != null) {
                mRecorder.stop();
                myChronometer.stop();
                mRecorder.reset();
                mRecorder.release();
                mRecorder = null;
                Toast.makeText(getApplicationContext(), "Recording stopped.", Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        finish();
        return true;
    }
}
