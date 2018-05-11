package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.sharing.SharedFolderMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CheckpointsActivity extends AppCompatActivity {

    String taskPath = "";
    File newFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkpoints);

        Bundle bundle = getIntent().getExtras();
        taskPath = bundle.getString("taskPath");

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Checkpoints");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        final EditText postalAddress = (EditText) findViewById(R.id.postal_address);
        final EditText dateInput = (EditText) findViewById(R.id.date_input);
        final EditText clientName = (EditText) findViewById(R.id.client_name);
        final EditText gasReading = (EditText) findViewById(R.id.gas_reading);
        final EditText electricityReading = (EditText) findViewById(R.id.electricity_reading);
        final EditText heatReading = (EditText) findViewById(R.id.heat_reading);
        final EditText waterReading = (EditText) findViewById(R.id.water_reading);
        final EditText gasLocation = (EditText) findViewById(R.id.gas_location);
        final EditText electricityLocation = (EditText) findViewById(R.id.electricity_location);
        final EditText heatLocation = (EditText) findViewById(R.id.heat_location);
        final EditText waterLocation = (EditText) findViewById(R.id.water_location);
        final EditText keyDescription = (EditText) findViewById(R.id.key_description);

        final Calendar myCalendar = Calendar.getInstance();
        final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear,
                                  int dayOfMonth) {
                myCalendar.set(Calendar.YEAR, year);
                myCalendar.set(Calendar.MONTH, monthOfYear);
                myCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                dateInput.setText(sdf.format(myCalendar.getTime()));
            }
        };

        dateInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(CheckpointsActivity.this, date, myCalendar
                        .get(Calendar.YEAR), myCalendar.get(Calendar.MONTH),
                        myCalendar.get(Calendar.DAY_OF_MONTH)).show();
            }
        });

        Button finishTaskBtn = (Button) findViewById(R.id.next_button_3);
        finishTaskBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (postalAddress.getText().toString().trim().isEmpty() ||
                        dateInput.getText().toString().trim().isEmpty() ||
                        clientName.getText().toString().trim().isEmpty() ||
                        gasReading.getText().toString().trim().isEmpty() ||
                        electricityReading.getText().toString().trim().isEmpty() ||
                        heatReading.getText().toString().trim().isEmpty() ||
                        waterReading.getText().toString().trim().isEmpty() ||
                        keyDescription.getText().toString().trim().isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Please fill all the fields to proceed.", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        File file = new File(taskPath + "/Checkpoints.pdf");
                        file.createNewFile();
                        FileOutputStream fOut = new FileOutputStream(file);
                        PdfDocument document = new PdfDocument();
                        PdfDocument.PageInfo pageInfo = new
                                PdfDocument.PageInfo.Builder(1000, 1000, 1).create();
                        PdfDocument.Page page = document.startPage(pageInfo);
                        Canvas canvas = page.getCanvas();
                        Paint paint = new Paint();

                        canvas.drawText("Postal address: " + postalAddress.getText().toString().trim(), 50, 50, paint);
                        canvas.drawText("Date: " + dateInput.getText().toString().trim(), 50, 100, paint);
                        canvas.drawText("Client Name: " + clientName.getText().toString().trim(), 50, 150, paint);
                        canvas.drawText("Gas Readings: " + gasReading.getText().toString().trim(), 50, 200, paint);
                        canvas.drawText("Gas Meter Location: " + gasLocation.getText().toString().trim(), 50, 250, paint);
                        canvas.drawText("Electricity Readings: " + electricityReading.getText().toString().trim(), 50, 300, paint);
                        canvas.drawText("Electricity Meter Location: " + electricityLocation.getText().toString().trim(), 50, 350, paint);
                        canvas.drawText("Heat Readings: " + heatReading.getText().toString().trim(), 50, 400, paint);
                        canvas.drawText("Heat Meter Location: " + heatLocation.getText().toString().trim(), 50, 450, paint);
                        canvas.drawText("Water Readings: " + waterReading.getText().toString().trim(), 50, 500, paint);
                        canvas.drawText("Water Meter Location: " + waterLocation.getText().toString().trim(), 50, 550, paint);
                        canvas.drawText("Key Description: " + keyDescription.getText().toString().trim(), 50, 600, paint);

                        document.finishPage(page);
                        document.writeTo(fOut);
                        document.close();
                    } catch (IOException e) {
                        Log.e("PDF creation error", e.getMessage());
                    }
                    AlertDialog.Builder alert = new AlertDialog.Builder(CheckpointsActivity.this);
                    alert.setTitle("Upload now?");
                    alert.setMessage("Do you want to upload the pending tasks to Dropbox now?");
                    alert.setPositiveButton("Yes, upload now", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (isNetworkAvailable()) {
                                startUploadingToDropbox();
                            } else {
                                Toast.makeText(getApplicationContext(), "No internet connection. Try later.", Toast.LENGTH_SHORT).show();
                                Intent i = new Intent(CheckpointsActivity.this, UserActivity.class);
                                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                            }
                        }
                    });
                    alert.setNegativeButton("No, upload later", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = new Intent(CheckpointsActivity.this, UserActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        }
                    });
                    alert.show();
                }
            }
        });

        ImageView gasReadingPhoto = (ImageView) findViewById(R.id.gas_reading_photo);
        gasReadingPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera("gas_reading");
            }
        });

        ImageView electricityReadingPhoto = (ImageView) findViewById(R.id.electricity_reading_photo);
        electricityReadingPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera("electricity_reading");
            }
        });

        ImageView heatReadingPhoto = (ImageView) findViewById(R.id.heat_reading_photo);
        heatReadingPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera("heat_reading");
            }
        });

        ImageView waterReadingPhoto = (ImageView) findViewById(R.id.water_reading_photo);
        waterReadingPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera("water_reading");
            }
        });

        ImageView keyPhoto = (ImageView) findViewById(R.id.key_photo);
        keyPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera("key_image");
            }
        });
    }

    private void openCamera(String imageName) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(CheckpointsActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            newFile = new File(taskPath + "/" + imageName + ".jpg");
            try {
                newFile.createNewFile();
            } catch (IOException e) {
                Log.e("Camera Error", e.getMessage());
            }

            Uri outputFileUri = Uri.fromFile(newFile);
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                    m.invoke(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            startActivityForResult(cameraIntent, 2);
        }
    }


    private void startUploadingToDropbox() {
        final ProgressDialog progressDialog = new ProgressDialog(CheckpointsActivity.this);
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
                    DbxClientV2 dbxClient = DropboxClientFactory.getClient();
                    List<SharedFolderMetadata> myList = dbxClient.sharing().listMountableFolders().getEntries();
                    SharedPreferences prefs = getSharedPreferences("login-info", MODE_PRIVATE);
                    final String clerkName = prefs.getString("clerk-name", null);

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
                                startActivity(UploadToDropboxActivity.getIntent(CheckpointsActivity.this, sfm.getPathLower()));
                                break;
                            }
                        }
                    }
                    if (sfm == null) {
                        Log.e("Error: ", "No folder with name: " + clerkName);
                        //progressDialog.dismiss();

                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(getApplicationContext(), "No folder has been shared by Manager with name '" + clerkName + "'", Toast.LENGTH_LONG).show();
                                Intent i = new Intent(CheckpointsActivity.this, UserActivity.class);
                                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                            }
                        });

                    }
                } catch (Exception e) {
                    Log.e("Start UsrActivity error", e.getMessage(), e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressDialog.dismiss();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        finish();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2 && resultCode == RESULT_OK) {
            Toast.makeText(getApplicationContext(), "Photo added successfully!", Toast.LENGTH_SHORT).show();
        } else if (requestCode == 2) {
            newFile.delete();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }
}
