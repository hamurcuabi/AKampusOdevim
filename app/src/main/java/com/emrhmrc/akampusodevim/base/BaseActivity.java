package com.emrhmrc.akampusodevim.base;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.emrhmrc.akampusodevim.R;
import com.emrhmrc.akampusodevim.helper.ImageHelper;
import com.emrhmrc.akampusodevim.helper.StringHelper;
import com.emrhmrc.akampusodevim.util.PermissionUtils;
import com.emrhmrc.sweetdialoglib.DialogCreater;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    private static String title;
    protected static final int MAX_DIMENSION = 1200;

    protected static final int GALLERY_PERMISSIONS_REQUEST = 0;
    protected static final int GALLERY_IMAGE_REQUEST = 1;
    protected static final int CAMERA_PERMISSIONS_REQUEST = 2;
    protected static final int CAMERA_IMAGE_REQUEST = 3;
    protected static final int RESULT_CROP = 4;
    @BindView(R.id.image_details)
    protected TextView image_details;
    @BindView(R.id.main_image)
    protected ImageView main_image;
    @BindView(R.id.fab)
    protected FloatingActionButton fab;
    @BindView(R.id.toolbar)
    protected Toolbar toolbar;
    protected List<String> expectedStrings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        onLoad();
        initialToolbar(title);

    }

    protected void initialToolbar(String text) {
        toolbar.setTitle(text);
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        setSupportActionBar(toolbar);
    }

    public static void setTitle(String title) {
        BaseActivity.title = title;
    }

    protected abstract void onLoad();

    protected abstract void detectText();

    protected void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap = ImageHelper.scaleBitmapDown(
                        MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                        MAX_DIMENSION);
                //callCloudVision(bitmap);
                main_image.setImageBitmap(bitmap);
                detectText();
            } catch (IOException e) {
                Log.e(TAG, "Image picking failed because " + e.getMessage());
                DialogCreater.errorDialog(this, getResources().getString(R.string.image_picker_error));
            }
        } else {
            Log.i(TAG, "Image picker gave us a null image.");
            DialogCreater.errorDialog(this, getResources().getString(R.string.image_picker_error));
        }
    }

    protected void lookExpectedWords() {

        int point = 0;
        for (String item : expectedStrings
        ) {
            if (image_details.getText().toString().contains(item.toLowerCase())) {
                point += 10;
                StringHelper.setHighLightedText(image_details, item);
            }

        }
        if (point > 0) {
            DialogCreater.succesDialog(this, getResources().getString(R.string.win_message, point));
        } else {
            DialogCreater.warningDialog(this, getResources().getString(R.string.loose_message));
        }

    }

    protected void performCrop(Uri picUri) {
        try {
            Intent cropIntent = new Intent("com.android.camera.action.CROP");

            cropIntent.setDataAndType(picUri, "image/*");
            // set crop properties
            cropIntent.putExtra("crop", "true");

          /*  cropIntent.putExtra("aspectX", 1);
            cropIntent.putExtra("aspectY", 1);

            cropIntent.putExtra("outputX", 280);
            cropIntent.putExtra("outputY", 280);*/
            // retrieve data on return
            cropIntent.putExtra("data", true);
            startActivityForResult(cropIntent, RESULT_CROP);
        } catch (ActivityNotFoundException ex) {
            DialogCreater.errorDialog(this, getResources().getString(R.string.crop_error));
        }
    }

    protected void openPickImageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setMessage(R.string.dialog_select_prompt)
                .setPositiveButton(R.string.dialog_select_gallery, (dialog, which) -> startGalleryChooser())
                .setNegativeButton(R.string.dialog_select_camera, (dialog, which) -> startCamera());
        builder.create().show();
    }

    protected void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,
                    getResources().getString(R.string.dialog_select_prompt)),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    protected void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    @OnClick({R.id.fab})
    public void pickImage() {
        openPickImageDialog();
    }


}
