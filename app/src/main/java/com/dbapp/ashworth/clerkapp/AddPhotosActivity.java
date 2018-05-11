package com.dbapp.ashworth.clerkapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mzelzoghbi.zgallery.ZGrid;
import com.mzelzoghbi.zgallery.entities.ZColor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class AddPhotosActivity extends AppCompatActivity {

    String taskPath = "";
    int count = 0;
    ArrayList<String> f = new ArrayList<>();// list of file paths
    File[] listFile;
    File newFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_photos);

        Bundle bundle = getIntent().getExtras();
        taskPath = bundle.getString("taskPath");

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Take Photos");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        Button addFolderBtn = (Button) findViewById(R.id.add_folder_button);
        addFolderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddFolderDialog();
            }
        });
        Button nextBtn = (Button) findViewById(R.id.next_button_2);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(AddPhotosActivity.this, CheckpointsActivity.class);
                i.putExtra("taskPath", taskPath);
                AddPhotosActivity.this.startActivity(i);
            }
        });
    }

    private void showAddFolderDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(AddPhotosActivity.this);
        alert.setTitle("Add new room");
        //alert.setMessage(" ");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        FrameLayout container = new FrameLayout(AddPhotosActivity.this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        input.setLayoutParams(params);
        container.addView(input);

        alert.setView(container);
        alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                if (input.getText().toString().length() > 0) {
                    addFolder(input.getText().toString());

//                LinearLayout folderListContainer = (LinearLayout) findViewById(R.id.ll);
//
//                Button b = new Button(AddPhotosActivity.this);
//                b.setText(value);
//                b.setBackgroundColor(Color.TRANSPARENT);
//                b.setAllCaps(false);
//                b.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_camera, 0);
//
//                folderListContainer.addView(LayoutInflater.from(AddPhotosActivity.this).inflate(R.layout.files_item,folderListContainer));
//                ImageView b= (ImageView)findViewById(R.id.open_camera);
//                b.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        Button tmp = (Button) v;
//                        openCamera("/" + tmp.getText() + "/");
//                    }
//                });

                    return;
                } else
                    Toast.makeText(getApplicationContext(), "Please enter a name to proceed", Toast.LENGTH_LONG).show();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });
        alert.show();
    }

    private void addFolder(String roomName) {
        ViewGroup parent = (ViewGroup) findViewById(R.id.ll);
        View C = getLayoutInflater().inflate(R.layout.files_item, parent, false);

        TextView folderName = (TextView) C.findViewById(R.id.folder_name);
        folderName.setText(roomName);

        ImageView openCameraBtn = (ImageView) C.findViewById(R.id.open_camera);
        openCameraBtn.setTag(folderName);

        C.setTag(folderName);
        C.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView tmp = (TextView) v.getTag();
//                Intent i = new Intent(AddPhotosActivity.this, ViewPhotosActivity.class);
//                i.putExtra("taskPath", taskPath+"/"+tmp.getText());
//                AddPhotosActivity.this.startActivity(i);
                getFilesList(taskPath + "/" + tmp.getText());

                ZGrid.with(AddPhotosActivity.this, f)
                        .setToolbarColorResId(R.color.dbx_primary) // toolbar color
                        .setTitle(tmp.getText().toString()) // toolbar title
                        .setToolbarTitleColor(ZColor.WHITE) // toolbar title color
                        .setSpanCount(2) // columns count
                        .setGridImgPlaceHolder(R.color.silver) // color placeholder for the grid image until it loads
                        .show();

//                ZGallery.with(AddPhotosActivity.this, f)
//                        .setToolbarTitleColor(ZColor.WHITE) // toolbar title color
//                        .setGalleryBackgroundColor(ZColor.BLACK) // activity background color
//                        .setToolbarColorResId(R.color.dbx_primary) // toolbar color
//                        .setTitle(tmp.getText().toString()) // toolbar title
//                        .show();
            }
        });

        openCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView tmp = (TextView) v.getTag();
                openCamera("/" + tmp.getText() + "/");
            }
        });

        parent.addView(C);
    }

    public void getFilesList(String roomPath) {
        File file = new File(roomPath);
        f.clear();
        if (file.isDirectory()) {
            listFile = file.listFiles();

            for (int i = 0; i < listFile.length; i++) {
                f.add(listFile[i].getAbsolutePath());
            }
        }
    }

    private void openCamera(String folderName) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(AddPhotosActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            count++;
            newFile = new File(taskPath + folderName);
            newFile.mkdirs();
            newFile = new File(taskPath + folderName + count + ".jpg");
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
            findViewById(R.id.next_button_2).setClickable(true);
            findViewById(R.id.next_button_2).setEnabled(true);
            Toast.makeText(getApplicationContext(), "Photo added successfully!", Toast.LENGTH_SHORT).show();
        } else if (requestCode == 2) {
            newFile.delete();
            count--;
        }
    }
}
