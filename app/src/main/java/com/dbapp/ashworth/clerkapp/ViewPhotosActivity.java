package com.dbapp.ashworth.clerkapp;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.mzelzoghbi.zgallery.ZGrid;
import com.mzelzoghbi.zgallery.entities.ZColor;

import java.io.File;
import java.util.ArrayList;

public class ViewPhotosActivity extends AppCompatActivity {

    String taskPath = "";
    ArrayList<String> f = new ArrayList<>();// list of file paths
    File[] listFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_photos);

        Bundle bundle = getIntent().getExtras();
        taskPath = bundle.getString("taskPath");

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(taskPath.substring(taskPath.lastIndexOf('/') + 1));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        getFromSdcard();
        ZGrid.with(this, f)
                .setToolbarColorResId(R.color.dbx_primary) // toolbar color
                .setTitle("Zak Gallery") // toolbar title
                .setToolbarTitleColor(ZColor.WHITE) // toolbar title color
                .setSpanCount(2) // columns count
                .setGridImgPlaceHolder(R.color.silver) // color placeholder for the grid image until it loads
                .show();
    }

    public void getFromSdcard() {
        File file = new File(taskPath);

        if (file.isDirectory()) {
            listFile = file.listFiles();

            for (int i = 0; i < listFile.length; i++) {
                f.add(listFile[i].getAbsolutePath());
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        finish();
        return true;
    }
}
