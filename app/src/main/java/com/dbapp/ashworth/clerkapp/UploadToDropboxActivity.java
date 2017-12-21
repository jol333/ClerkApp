package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;

import java.io.File;
import java.util.ArrayList;

import static com.dbapp.ashworth.clerkapp.DropboxClientFactory.getClient;


/**
 * Activity that displays the content of a path in dropbox and lets users navigate folders,
 * and upload/download files
 */
public class UploadToDropboxActivity extends DropboxActivity {
    public final static String EXTRA_PATH = "AudioRecordActivity_Path";
    private static final String TAG = UploadToDropboxActivity.class.getName();
    private static final int PICKFILE_REQUEST_CODE = 1;
    ProgressDialog progressBar;
    ArrayList<File> f;
    private String mPath;
    private String appPath;
    private FilesAdapter mFilesAdapter;
    private FileMetadata mSelectedFile;
    private int progressBarStatus = 0;
    private Handler progressBarHandler = new Handler();
    private int i = 0;

    public static Intent getIntent(Context context, String path) {
        Intent filesIntent = new Intent(context, UploadToDropboxActivity.class);
        filesIntent.putExtra(UploadToDropboxActivity.EXTRA_PATH, path);
        return filesIntent;
    }

    public static void listf(String directoryName, ArrayList<File> files) {
        File directory = new File(directoryName);

        // get all the files from a directory
        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                files.add(file);
            } else if (file.isDirectory()) {
                listf(file.getAbsolutePath(), files);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        mPath = path == null ? "" : path;

        setContentView(R.layout.activity_audio_record);

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Uploading files");

        //RecyclerView recyclerView = (RecyclerView) findViewById(R.id.files_list);
        mFilesAdapter = new FilesAdapter(PicassoClient.getPicasso(), new FilesAdapter.Callback() {
            @Override
            public void onFolderClicked(FolderMetadata folder) {
                startActivity(UploadToDropboxActivity.getIntent(UploadToDropboxActivity.this, folder.getPathLower()));
            }

            @Override
            public void onFileClicked(final FileMetadata file) {
                mSelectedFile = file;
                performWithPermissions(FileAction.DOWNLOAD);
            }
        });
        //recyclerView.setLayoutManager(new LinearLayoutManager(this));
        //recyclerView.setAdapter(mFilesAdapter);

        mSelectedFile = null;
        f = new ArrayList<>();
        appPath = Environment.getExternalStorageDirectory() + "/ClerkApp";
        listf(appPath, f);
        uploadFile(f);
        //showUploadProgress(f);
    }

    public void showUploadProgress(final ArrayList<File> files) {
        // creating progress bar dialog
        progressBar = new ProgressDialog(this);
        progressBar.setCancelable(false);
        progressBar.setMessage("Uploading files...");
        progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressBar.setProgress(0);
        final int noOfFiles = files.size();
        progressBar.setMax(noOfFiles);
        progressBar.show();
        //reset progress bar and file size status
        progressBarStatus = 0;
        i = 0;

        new Thread(new Runnable() {
            public void run() {
                while (progressBarStatus < noOfFiles) {
                    // performing operation
                    progressBarStatus = doOperation(files);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // Updating the progress bar
                    progressBarHandler.post(new Runnable() {
                        public void run() {
                            progressBar.setProgress(progressBarStatus);
                        }
                    });
                }
                // performing operation if file is downloaded,
                if (progressBarStatus >= noOfFiles) {
                    // sleeping for 1 second after operation completed
                    try {
                        Thread.sleep(1000);
                        File dir = new File(appPath);
                        deleteRecursive(dir);
                        dir.mkdirs();
                        //Toast.makeText(getApplicationContext(), "Upload finished.", Toast.LENGTH_LONG).show();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // close the progress bar dialog
                    progressBar.dismiss();
                    Intent i = new Intent(UploadToDropboxActivity.this, UserActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }
            }
        }).start();
    }

    public void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    public int doOperation(final ArrayList<File> files) {
        if (i < files.size()) {
            String fileUri = Uri.fromFile(files.get(i)).toString();
            String s = (files.get(i).getAbsolutePath());
            s = s.replace(appPath, "");
            s = s.substring(0, s.lastIndexOf("/"));
            Log.e("fileUri", fileUri);
            Log.e("path", mPath + s);

            new UploadFileTask(this, getClient(), new UploadFileTask.Callback() {
                @Override
                public void onUploadComplete(FileMetadata result) {
                    i++;
                }

                @Override
                public void onError(Exception e) {
                    progressBar.dismiss();
                    Log.e(TAG, "Failed to upload file.", e);
                    Toast.makeText(UploadToDropboxActivity.this,
                            "An error has occurred. Please try again.",
                            Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
            }).execute(fileUri, mPath + s);
            return i;
        }
        return 1000;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int actionCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        FileAction action = FileAction.fromCode(actionCode);

        boolean granted = true;
        for (int i = 0; i < grantResults.length; ++i) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Log.w(TAG, "User denied " + permissions[i] +
                        " permission to perform file action: " + action);
                granted = false;
                break;
            }
        }

        if (granted) {
            performAction(action);
        } else {
            switch (action) {
                case UPLOAD:
                    Toast.makeText(this,
                            "Can't upload file: read access denied. " +
                                    "Please grant storage permissions to use this functionality.",
                            Toast.LENGTH_LONG)
                            .show();
                    break;
                case DOWNLOAD:
                    Toast.makeText(this,
                            "Can't download file: write access denied. " +
                                    "Please grant storage permissions to use this functionality.",
                            Toast.LENGTH_LONG)
                            .show();
                    break;
            }
        }
    }

    private void performAction(FileAction action) {
        switch (action) {
            case UPLOAD:
                //launchFilePicker();
                break;
            case DOWNLOAD:
                if (mSelectedFile != null) {
                    //downloadFile(mSelectedFile);
                } else {
                    Log.e(TAG, "No file selected to download.");
                }
                break;
            default:
                Log.e(TAG, "Can't perform unhandled file action: " + action);
        }
    }

    @Override
    protected void loadData() {
        new ListFolderTask(getClient(), new ListFolderTask.Callback() {
            @Override
            public void onDataLoaded(ListFolderResult result) {
                mFilesAdapter.setFiles(result.getEntries());
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to list folder.", e);
                Toast.makeText(UploadToDropboxActivity.this,
                        "Failed to list files and folders.",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }).execute(mPath);
    }

    private void uploadFile(final ArrayList<File> files) {
        String fileUri = Uri.fromFile(files.get(files.size() - 1)).toString();
        String s = (files.get(files.size() - 1)).getAbsolutePath();
        s = s.replace(appPath, "");
        s = s.substring(0, s.lastIndexOf("/"));
        Log.e("fileUri", fileUri);
        Log.e("path", mPath + s);

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setTitle("Uploading...");
        dialog.setMessage(files.size()+" file(s) remaining...");
        dialog.show();

        new UploadFileTask(this, getClient(), new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {
                dialog.dismiss();

//                String message = result.getName() + " size " + result.getSize() + " modified " +
//                        DateFormat.getDateTimeInstance().format(result.getClientModified());
//                Toast.makeText(UploadToDropboxActivity.this, message, Toast.LENGTH_LONG)
//                        .show();

                if (files.size() > 1) {
                    files.remove(files.size() - 1);
                    uploadFile(files);
                } else {
                    File dir = new File(appPath);
                    deleteRecursive(dir);
                    dir.mkdirs();
                    Toast.makeText(getApplicationContext(), "Upload finished.", Toast.LENGTH_LONG).show();
                    Intent i = new Intent(UploadToDropboxActivity.this, UserActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                }
                // Reload the folder
                //loadData();
            }

            public void deleteRecursive(File fileOrDirectory) {

                if (fileOrDirectory.isDirectory()) {
                    for (File child : fileOrDirectory.listFiles()) {
                        deleteRecursive(child);
                    }
                }

                fileOrDirectory.delete();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(UploadToDropboxActivity.this,
                        "An error has occurred",
                        Toast.LENGTH_SHORT)
                        .show();
                dialog.dismiss();
                Intent i = new Intent(UploadToDropboxActivity.this, UserActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);

                Log.e(TAG, "Failed to upload file.", e);
            }
        }).execute(fileUri, mPath + s);
    }

    private void performWithPermissions(final FileAction action) {
        if (hasPermissionsForAction(action)) {
            performAction(action);
            return;
        }

        if (shouldDisplayRationaleForAction(action)) {
            new AlertDialog.Builder(this)
                    .setMessage("This app requires storage access to download and upload files.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissionsForAction(action);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            requestPermissionsForAction(action);
        }
    }

    private boolean hasPermissionsForAction(FileAction action) {
        for (String permission : action.getPermissions()) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            if (result == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldDisplayRationaleForAction(FileAction action) {
        for (String permission : action.getPermissions()) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    private void requestPermissionsForAction(FileAction action) {
        ActivityCompat.requestPermissions(
                this,
                action.getPermissions(),
                action.getCode()
        );
    }

    private enum FileAction {
        DOWNLOAD(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        UPLOAD(Manifest.permission.READ_EXTERNAL_STORAGE);

        private static final FileAction[] values = values();

        private final String[] permissions;

        FileAction(String... permissions) {
            this.permissions = permissions;
        }

        public static FileAction fromCode(int code) {
            if (code < 0 || code >= values.length) {
                throw new IllegalArgumentException("Invalid FileAction code: " + code);
            }
            return values[code];
        }

        public int getCode() {
            return ordinal();
        }

        public String[] getPermissions() {
            return permissions;
        }
    }
}
