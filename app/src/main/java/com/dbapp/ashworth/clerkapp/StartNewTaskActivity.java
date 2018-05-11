package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
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
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class StartNewTaskActivity extends AppCompatActivity {
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private File mAudioFile;
    private MediaPlayer mPlayer = null;
    private EditText taskName;
    private Chronometer myChronometer;
    private Button nextButton;
    private Button recordButton;
    private Button stopButton;
    private Button retryButton;
    private Button playButton;
    private Button pauseButton;
    private Button resumeButton;
    private long mLastStopTime = 0;
    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    public static void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_new_task);

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Record audio");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        nextButton = (Button) findViewById(R.id.next_button_1);
        recordButton = (Button) findViewById(R.id.record_button);
        stopButton = (Button) findViewById(R.id.stop_button);
        retryButton = (Button) findViewById(R.id.retry_button);
        playButton = (Button) findViewById(R.id.play_button);
        pauseButton = (Button) findViewById(R.id.pause_button);
        resumeButton = (Button) findViewById(R.id.resume_button);
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
                    File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp/" + taskName.getText());
                    mydir.mkdirs();
                    requestAudioPermissions();
                }
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                stopButton.setVisibility(View.GONE);
                pauseButton.setVisibility(View.GONE);
                stopRecording(false);
                resumeButton.setVisibility(View.VISIBLE);
                myChronometer.stop();
                mLastStopTime = SystemClock.elapsedRealtime();
            }
        });

        resumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                resumeButton.setVisibility(View.GONE);
                startRecording(true);
                myChronometer.setBase(myChronometer.getBase() + SystemClock.elapsedRealtime() - mLastStopTime);
                myChronometer.start();
                pauseButton.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.VISIBLE);

            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(StartNewTaskActivity.this);
                builder
                        .setTitle("Stop Recording?")
                        .setMessage("Do you really want to stop recording the audio? If you wish to resume later, please tap pause instead.")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                stopButton.setVisibility(View.GONE);
                                pauseButton.setVisibility(View.GONE);
                                Toast.makeText(getApplicationContext(), "Please wait while we are saving the recorded file...", Toast.LENGTH_LONG).show();
                                stopRecording(true);
                                retryButton.setVisibility(View.VISIBLE);
                                playButton.setVisibility(View.VISIBLE);

                                nextButton.setClickable(true);
                                nextButton.setEnabled(true);

                                myChronometer.stop();
                                mLastStopTime = 0;
                                Toast.makeText(getApplicationContext(), "Recording saved successfully!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("No", null)                        //Do nothing on no
                        .show();
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
                                mAudioFile.delete();
                                retryButton.setVisibility(View.GONE);
                                playButton.setVisibility(View.GONE);
                                recordButton.setVisibility(View.VISIBLE);
                                myChronometer.setVisibility(View.GONE);
                                taskName.setVisibility(View.VISIBLE);

                                //nextButton.setAlpha(0.25f);
                                nextButton.setClickable(false);
                                nextButton.setEnabled(false);
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
                        if (mAudioFile == null) {
                            Toast.makeText(getApplicationContext(), "No file to play. Please record again.", Toast.LENGTH_SHORT).show();
                            if (mPlayer != null) {
                                mPlayer.release();
                            }
                            playButton.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_play_arrow_black_24dp), null, null);
                            playButton.setText("Play");
                            retryButton.setVisibility(View.GONE);
                            playButton.setVisibility(View.GONE);
                            recordButton.setVisibility(View.VISIBLE);
                            myChronometer.setVisibility(View.GONE);
                            taskName.setVisibility(View.VISIBLE);

                            //nextButton.setAlpha(0.25f);
                            nextButton.setClickable(false);
                            nextButton.setEnabled(false);
                            return;
                        }
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
                i.putExtra("taskPath", Environment.getExternalStorageDirectory() + "/ClerkApp/" + taskName.getText());
                StartNewTaskActivity.this.startActivity(i);
            }
        });
    }

    private String getFilename() {
        String f = Environment.getExternalStorageDirectory() + "/ClerkApp/" +
                taskName.getText() + "/" + taskName.getText() + ".wav";
        mAudioFile = new File(f);
        try {
            mAudioFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (mAudioFile.getAbsolutePath());
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath + "/" + AUDIO_RECORDER_TEMP_FILE);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (file.getAbsolutePath());
    }

    private void startRecording(final boolean b) {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSize);

        recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                writeAudioDataToFile(b);
            }
        }, "AudioRecorder Thread");

        recordingThread.start();
    }

    private void stopRecording(boolean b) {
        if (recorder != null) {
            isRecording = false;

            recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

        if (b == true) {
            copyWaveFile(getTempFilename(), getFilename());
            deleteTempFile();
        }

        long totalDuration = getSoundDuration();

        Log.i("Recorder", "Duration " + totalDuration);
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    private void writeAudioDataToFile(boolean b) {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename, b);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int read = 0;

        if (os != null) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 44;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename, true);
            totalAudioLen = in.getChannel().size() + out.getChannel().size();
            totalDataLen = totalAudioLen + 44;

            Log.i("Recorder", "Out channel size initially " + out.getChannel().size());

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate, outFilename);
            Log.i("Recorder", "Out channel size after header write " + out.getChannel().size());


            while (in.read(data) != -1) {
                out.write(data);
            }
            Log.i("Recorder", "Out channel size" + out.getChannel().size());
            Log.i("Recorder", "in channel size" + in.getChannel().size());
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate, String outFileName) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        //out.write(header, 0, 44);
        //out.getChannel().position(0).write(ByteBuffer.wrap(header));

        RandomAccessFile rFile = new RandomAccessFile(outFileName, "rw");
        rFile.seek(0);
        rFile.write(header, 0, 44);
        rFile.close();


    }

    public long getSoundDuration() {
        File file = new File(getFilename());
        long filesiZe = file.length();
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;
        long duration = filesiZe / byteRate;
        return duration;
    }

    @Override
    public void onRequestPermissionsResult(int actionCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (actionCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    recordButton.setVisibility(View.GONE);
                    stopButton.setVisibility(View.VISIBLE);
                    pauseButton.setVisibility(View.VISIBLE);
                    taskName.setVisibility(View.GONE);
                    myChronometer.setVisibility(View.VISIBLE);
                    startRecording();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions denied to record audio", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private boolean requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_SHORT).show();

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
            recordButton.setVisibility(View.GONE);
            pauseButton.setVisibility(View.VISIBLE);
            stopButton.setVisibility(View.VISIBLE);
            taskName.setVisibility(View.GONE);
            myChronometer.setVisibility(View.VISIBLE);
            startRecording();
            return true;
        }
        return false;
    }

    private boolean startRecording() {
        Toast.makeText(getApplicationContext(), "Recording started...", Toast.LENGTH_SHORT).show();
        try {
            // File storageDir = getApplicationContext().getCacheDir();
            // mAudioFile = File.createTempFile(taskName.getText().toString(), ".wav", storageDir);

            startRecording(false);

            myChronometer.setBase(SystemClock.elapsedRealtime());
            myChronometer.stop();
            myChronometer.start();

            return true;
        } catch (Exception e) {
            Log.e("Record error", e.toString());
            return false;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        //finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.men);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onDestroy() {
        if (mPlayer != null)
            if (mPlayer.isPlaying())
                mPlayer.release();
        if (recorder != null)
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
                recorder.release();
            }
        super.onDestroy();
    }


    @Override
    public void onBackPressed() {
        //super.onBackPressed();

        if (taskName.length() > 3) {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Close Task")
                    .setMessage("Do you really want to exit this task? All recorded files will be lost!")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (mPlayer != null) mPlayer.release();
                            if (recorder != null)
                                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                                    recorder.stop();
                                    recorder.release();
                                }
                            myChronometer.stop();
                            File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp/" + taskName.getText());
                            if (mydir.exists())
                                deleteRecursive(mydir);
                            finish();
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        } else super.onBackPressed();
    }
}
