package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.RetryException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.UploadErrorException;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.UploadSessionFinishErrorException;
import com.dropbox.core.v2.files.UploadSessionLookupErrorException;
import com.dropbox.core.v2.files.WriteMode;

import org.zeroturnaround.zip.NameMapper;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.dbapp.ashworth.clerkapp.DropboxClientFactory.getClient;


/**
 * Activity that displays the content of a path in dropbox and lets users navigate folders,
 * and upload/download files
 */
public class UploadToDropboxActivity extends DropboxActivity {
    public final static String EXTRA_PATH = "AudioRecordActivity_Path";
    private static final String TAG = UploadToDropboxActivity.class.getName();
    private static final long CHUNKED_UPLOAD_CHUNK_SIZE = 4L << 20; // 8MiB
    private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;
    public static ProgressDialog progressBar;
    String progressMsg;
    private String mPath;
    private String appPath;
    private FilesAdapter mFilesAdapter;
    private FileMetadata mSelectedFile;
    private int i = 0;

    public static Intent getIntent(Context context, String path) {
        Intent filesIntent = new Intent(context, UploadToDropboxActivity.class);
        filesIntent.putExtra(UploadToDropboxActivity.EXTRA_PATH, path);
        return filesIntent;
    }

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

        String path = getIntent().getStringExtra(EXTRA_PATH);
        mPath = path == null ? "" : path;

        setContentView(R.layout.activity_audio_record);

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Uploading files");

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

        mSelectedFile = null;
        appPath = Environment.getExternalStorageDirectory() + "/ClerkApp";

        showUploadProgress();
    }

    public void showUploadProgress() {
        // creating progress bar dialog
        progressBar = new ProgressDialog(this);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                progressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressBar.setCancelable(false);
                progressBar.setMessage("Zipping files...");
                progressBar.show();
            }

            @Override
            protected Void doInBackground(Void... voids) {
                final String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HHmmss").format(new Date());
                ZipUtil.pack(new File(appPath), new File(Environment.getExternalStorageDirectory() + "/" + timeStamp + ".zip"), new NameMapper() {
                    @Override
                    public String map(String name) {
                        return timeStamp + "/" + name;
                    }
                });
                // doOperation(Environment.getExternalStorageDirectory() +"/"+timeStamp+".zip");
                File localFile = new File(Environment.getExternalStorageDirectory() + "/" + timeStamp + ".zip");
                if (localFile.length() <= (2 * CHUNKED_UPLOAD_CHUNK_SIZE)) {
                    uploadFile(DropboxClientFactory.getClient(), localFile, mPath);
                } else {
                    chunkedUploadFile(DropboxClientFactory.getClient(), localFile, mPath);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

            }

        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
    }

    public int doOperation(final String zipPath) {

        String fileUri = Uri.fromFile(new File(zipPath)).toString();

        new UploadFileTask(this, getClient(), new UploadFileTask.Callback() {
            @Override
            public void onUploadComplete(FileMetadata result) {
                new File(zipPath).delete();
                File dir = new File(appPath);
                deleteRecursive(dir);
                dir.mkdirs();
                // close the progress bar dialog
                progressBar.dismiss();
                Intent i = new Intent(UploadToDropboxActivity.this, UserActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                Toast.makeText(getApplicationContext(), "Upload complete!", Toast.LENGTH_LONG).show();
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
        }).execute(fileUri, mPath);
        return 1;
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

    private void dismissUpload(final String msg) {
        progressBar.dismiss();
        Intent i = new Intent(UploadToDropboxActivity.this, UserActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        runOnUiThread(new Runnable() {
            public void run() {

                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
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

    // =====================================================================================
    // =====================================================================================
    // =====================================================================================

    private void uploadFile(DbxClientV2 dbxClient, File localFile, String dropboxPath) {
        runOnUiThread(new Runnable() {
            public void run() {
                progressBar.setMessage("Uploading files...");
            }
        });

        try (InputStream in = new FileInputStream(localFile)) {
            Log.e("Test", localFile.getAbsolutePath() + " " + dropboxPath);
            FileMetadata metadata = dbxClient.files().uploadBuilder(dropboxPath + "/" + localFile.getName())
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(in);

            Log.e("Metadata", metadata.toStringMultiline());
            File dir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp");
            deleteRecursive(dir);
            dismissUpload("Upload successful!");

        } catch (UploadErrorException ex) {
            Log.e("Dropbox Upload Error", ex.getMessage());
            dismissUpload("Upload error!");
        } catch (DbxException ex) {
            Log.e("Dropbox Upload Error", ex.getMessage());
            dismissUpload("Upload error!");
        } catch (IOException ex) {
            Log.e("Error reading from file", "\"" + localFile + "\": " + ex.getMessage());
            dismissUpload("Cannot access audit task files!");
        }
    }

    // =====================================================================================

    private void chunkedUploadFile(DbxClientV2 dbxClient, File localFile, String dropboxPath) {
        runOnUiThread(new Runnable() {
            public void run() {
                progressBar.setMessage("Uploading files...");
            }
        });
        long size = localFile.length();
        long uploaded = 0L;
        DbxException thrown = null;
        String sessionId = null;
        for (i = 0; i < CHUNKED_UPLOAD_MAX_ATTEMPTS; ++i) {
            if (i > 0) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        progressBar.setMessage("Retrying upload (" + (i + 1) + " / " + CHUNKED_UPLOAD_MAX_ATTEMPTS + " attempts)");
                    }
                });
            }

            try (InputStream in = new FileInputStream(localFile)) {
                // if this is a retry, make sure seek to the correct offset
                in.skip(uploaded);

                // (1) Start
                if (sessionId == null) {
                    sessionId = dbxClient.files().uploadSessionStart()
                            .uploadAndFinish(in, CHUNKED_UPLOAD_CHUNK_SIZE)
                            .getSessionId();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    printProgress(uploaded, size);
                }

                UploadSessionCursor cursor = new UploadSessionCursor(sessionId, uploaded);

                // (2) Append
                while ((size - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    dbxClient.files().uploadSessionAppendV2(cursor)
                            .uploadAndFinish(in, CHUNKED_UPLOAD_CHUNK_SIZE);
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    printProgress(uploaded, size);
                    cursor = new UploadSessionCursor(sessionId, uploaded);
                }

                // (3) Finish
                long remaining = size - uploaded;
                CommitInfo commitInfo = CommitInfo.newBuilder(dropboxPath + "/" + localFile.getName())
                        .withMode(WriteMode.OVERWRITE)
                        .withClientModified(new Date(localFile.lastModified()))
                        .build();
                FileMetadata metadata = dbxClient.files().uploadSessionFinish(cursor, commitInfo)
                        .uploadAndFinish(in, remaining);

                Log.e("Metadata", metadata.toStringMultiline());
                File dir = new File(Environment.getExternalStorageDirectory() + "/ClerkApp");
                deleteRecursive(dir);
                dismissUpload("Upload successful!");
                return;
            } catch (RetryException ex) {
                thrown = ex;
                // RetryExceptions are never automatically retried by the client for uploads. Must
                // catch this exception even if DbxRequestConfig.getMaxRetries() > 0.
                sleepQuietly(ex.getBackoffMillis());
                continue;
            } catch (NetworkIOException ex) {
                thrown = ex;
                // network issue with Dropbox (maybe a timeout?) try again
                continue;
            } catch (UploadSessionLookupErrorException ex) {
                if (ex.errorValue.isIncorrectOffset()) {
                    thrown = ex;
                    // server offset into the stream doesn't match our offset (uploaded). Seek to
                    // the expected offset according to the server and try again.
                    uploaded = ex.errorValue
                            .getIncorrectOffsetValue()
                            .getCorrectOffset();
                    continue;
                } else {
                    // Some other error occurred, give up.
                    Log.e("Dropbox Upload Error", ex.getMessage());
                    dismissUpload("Upload error!");
                    return;
                }
            } catch (UploadSessionFinishErrorException ex) {
                if (ex.errorValue.isLookupFailed() && ex.errorValue.getLookupFailedValue().isIncorrectOffset()) {
                    thrown = ex;
                    // server offset into the stream doesn't match our offset (uploaded). Seek to
                    // the expected offset according to the server and try again.
                    uploaded = ex.errorValue
                            .getLookupFailedValue()
                            .getIncorrectOffsetValue()
                            .getCorrectOffset();
                    continue;
                } else {
                    // some other error occurred, give up.
                    Log.e("Dropbox Upload Error", ex.getMessage());
                    dismissUpload("Upload error!");
                    return;
                }
            } catch (DbxException ex) {
                Log.e("Dropbox Upload Error", ex.getMessage());
                dismissUpload("Upload error!");
                return;
            } catch (IOException ex) {
                Log.e("Error reading from file", "\"" + localFile + "\": " + ex.getMessage());
                dismissUpload("Cannot access audit task files!");
                return;
            }
        }

        // if we made it here, then we must have run out of attempts
        Log.e("Max out upload attempts", thrown.getMessage());
        dismissUpload("Maxed out upload attempts. Please check your internet connection");
    }

    private void printProgress(long uploaded, long size) {
        progressMsg = String.format("Uploaded %12d / %12d bytes \n(%5.2f%%)", uploaded, size, 100 * (uploaded / (double) size));
        runOnUiThread(new Runnable() {
            public void run() {
                progressBar.setMessage(progressMsg);
            }
        });
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            // just exit
            Log.e("Sleep Interrupt Error", "Error uploading to Dropbox: interrupted during backoff.");
            dismissUpload("Upload error!");
        }
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
