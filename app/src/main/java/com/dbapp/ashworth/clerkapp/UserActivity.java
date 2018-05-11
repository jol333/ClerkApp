package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.sharing.SharedFolderMetadata;
import com.dropbox.core.v2.users.FullAccount;

import java.io.File;
import java.util.List;

public class UserActivity extends DropboxActivity {

    String clerkName = "";
    Boolean clerkNameCorrect = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_user);

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);

        Button loginButton = (Button) findViewById(R.id.login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Auth.startOAuth2Authentication(UserActivity.this, getString(R.string.app_key));
            }
        });

        Button filesButton = (Button) findViewById(R.id.files_button);
        filesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //startActivity(FilesActivity.getIntent(UserActivity.this, ""));
                //startUploadingToDropbox();
                if (isStoragePermissionGranted()) {
                    Intent intent = new Intent(UserActivity.this, StartNewTaskActivity.class);
                    startActivity(intent);
                } else
                    Toast.makeText(getApplicationContext(), "You cannot start a task without giving storage access", Toast.LENGTH_LONG).show();
            }
        });

        Button pendingUploadsButton = (Button) findViewById(R.id.pending_uploads_button);
        pendingUploadsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPendingUploads();
            }
        });

        File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp");
        if (mydir.listFiles() != null)
            pendingUploadsButton.setText("Pending Uploads (" + mydir.listFiles().length + ")");

        /*
        if (isStoragePermissionGranted()) {
            File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp");
            mydir.mkdirs();
            pendingUploadsButton.setText("Pending Uploads (" + mydir.listFiles().length + ")");
        }
        */
    }

    public void showPendingUploads() {
        final String m_chosenDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ClerkApp";
        //Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()+"ClerkApp";

        if (isStoragePermissionGranted()) {
            File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp");
            mydir.mkdirs();
            if (mydir.listFiles().length == 0) {
                Toast.makeText(getApplicationContext(), "No pending uploads found.", Toast.LENGTH_LONG).show();
                ((Button) findViewById(R.id.pending_uploads_button)).setText("Pending Uploads (0)");
                return;
            }
            // Create DirectoryChooserDialog and register a callback
            DirectoryChooserDialog directoryChooserDialog =
                    new DirectoryChooserDialog(UserActivity.this,
                            new DirectoryChooserDialog.ChosenDirectoryListener() {
                                @Override
                                public void onChosenDir(String chosenDir) {
                                    if (isNetworkAvailable()) {
                                        startUploadingToDropbox();
                                    } else {
                                        Toast.makeText(getApplicationContext(), "No internet connection", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
            // Load directory chooser dialog for initial 'm_chosenDir' directory.
            // The registered callback will be called upon final directory selection.
            directoryChooserDialog.chooseDirectory(m_chosenDir);
        } else {
            Toast.makeText(getApplicationContext(), "You can't see the pending uploads without giving access to storage", Toast.LENGTH_LONG).show();
        }
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.e("Permission is granted", "");
                return true;
            } else {

                Log.e("Permission is revoked", "");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.e("Permission is granted", "");
            return true;
        }
    }

    private void startUploadingToDropbox() {
        final ProgressDialog progressDialog = new ProgressDialog(UserActivity.this);
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setCancelable(false);
                progressDialog.setMessage("Connecting to Dropbox...");
                progressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    clerkNameCorrect = true;
                    DbxClientV2 dbxClient = DropboxClientFactory.getClient();
                    List<SharedFolderMetadata> myList = dbxClient.sharing().listMountableFolders().getEntries();

                    SharedFolderMetadata sfm = null;
                    for (SharedFolderMetadata folder : myList) {
                        if (folder != null) {
                            if (folder.getName().toLowerCase().equals(clerkName.toLowerCase())) {
                                if (folder.getPathLower() == null) {
                                    sfm = dbxClient.sharing().mountFolder(folder.getSharedFolderId());
                                } else {
                                    sfm = folder;
                                }
                                finishAffinity();
                                startActivity(UploadToDropboxActivity.getIntent(UserActivity.this, sfm.getPathLower()));
                                break;
                            }
                        }
                    }
                    if (sfm == null) {
                        Log.e("Error: ", "No folder with name: " + clerkName);
                        //progressDialog.dismiss();
                        clerkNameCorrect = false;
                        //showFolderNameDialog();

                    }
                } catch (Exception e) {
                    Log.e("Start UsrActivity error", e.getMessage(), e);
                    progressDialog.dismiss();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressDialog.dismiss();

                if (!clerkNameCorrect) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            showFolderNameDialog();
                        }
                    });
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
    }

    private void showFolderNameDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(UserActivity.this);
        alert.setMessage("No folder has been shared by Manager with name: " + clerkName + ". Please contact your manager and enter exact name of the shared folder.");
        //alert.setMessage(" ");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        FrameLayout container = new FrameLayout(UserActivity.this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        input.setLayoutParams(params);
        container.addView(input);

        alert.setView(container);
        alert.setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                clerkName = input.getText().toString().trim();
                SharedPreferences prefs = getSharedPreferences("login-info", MODE_PRIVATE);
                String storedName = prefs.getString("clerk-name", null);
                if (clerkName != null && !clerkName.equals(storedName))
                    prefs.edit().putString("clerk-name", clerkName).apply();
                startUploadingToDropbox();
                return;
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });
        alert.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Log.d("permission", "granted");
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission
                    Toast.makeText(getApplicationContext(), "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();

                    //app cannot function without this permission for now so close it...
                    //onDestroy();
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Button pendingUploadsButton = (Button) findViewById(R.id.pending_uploads_button);
        File mydir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp");
        if (mydir.listFiles() != null)
            pendingUploadsButton.setText("Pending Uploads (" + mydir.listFiles().length + ")");

        if (hasToken()) {
            findViewById(R.id.login_button).setVisibility(View.GONE);
            findViewById(R.id.email_text).setVisibility(View.VISIBLE);
            findViewById(R.id.name_text).setVisibility(View.VISIBLE);
            //findViewById(R.id.type_text).setVisibility(View.VISIBLE);
            findViewById(R.id.files_button).setVisibility(View.VISIBLE);
            findViewById(R.id.files_button).setEnabled(true);
            findViewById(R.id.pending_uploads_button).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.login_button).setVisibility(View.VISIBLE);
            findViewById(R.id.email_text).setVisibility(View.GONE);
            findViewById(R.id.name_text).setVisibility(View.GONE);
            //findViewById(R.id.type_text).setVisibility(View.GONE);
            findViewById(R.id.files_button).setVisibility(View.GONE);
            findViewById(R.id.files_button).setEnabled(false);
            findViewById(R.id.pending_uploads_button).setVisibility(View.GONE);
        }
    }

    @Override
    protected void loadData() {
        new GetCurrentAccountTask(DropboxClientFactory.getClient(), new GetCurrentAccountTask.Callback() {
            @Override
            public void onComplete(FullAccount result) {
                clerkName = result.getName().getDisplayName();
                ((TextView) findViewById(R.id.email_text)).setText(result.getEmail());
                ((TextView) findViewById(R.id.name_text)).setText(clerkName);
                //((TextView) findViewById(R.id.type_text)).setText(result.getAccountType().name());

                SharedPreferences prefs = getSharedPreferences("login-info", MODE_PRIVATE);
                String storedName = prefs.getString("clerk-name", null);
                if (clerkName != null && !clerkName.equals(storedName))
                    prefs.edit().putString("clerk-name", clerkName).apply();
            }

            @Override
            public void onError(Exception e) {
                Log.e(getClass().getName(), "Failed to get account details.", e);
            }
        }).execute();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

}
