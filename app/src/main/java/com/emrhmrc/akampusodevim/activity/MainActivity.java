/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.emrhmrc.akampusodevim.activity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.emrhmrc.akampusodevim.R;
import com.emrhmrc.akampusodevim.base.BaseActivity;
import com.emrhmrc.akampusodevim.helper.ImageHelper;
import com.emrhmrc.akampusodevim.helper.StringHelper;
import com.emrhmrc.akampusodevim.util.PackageManagerUtils;
import com.emrhmrc.akampusodevim.util.PermissionUtils;
import com.emrhmrc.sweetdialoglib.DialogCreater;
import com.emrhmrc.sweetdialoglib.SweetAlertDialog;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.emrhmrc.akampusodevim.helper.Constants.ANDROID_CERT_HEADER;
import static com.emrhmrc.akampusodevim.helper.Constants.ANDROID_PACKAGE_HEADER;
import static com.emrhmrc.akampusodevim.helper.Constants.CLOUD_VISION_API_KEY;


public class MainActivity extends BaseActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onLoad() {
        setTitle("ClientSide");
        loadExpectedStrings();
    }

    private void loadExpectedStrings() {
        if (expectedStrings == null)
            expectedStrings = new ArrayList<>();
        expectedStrings.add("erişim");
        expectedStrings.add("ile");
        expectedStrings.add("ver");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            performCrop(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            performCrop(data.getData());
        } else if (requestCode == RESULT_CROP) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                uploadImage(uri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
                    startCamera();
                }
                break;
            case GALLERY_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
                    startGalleryChooser();
                }
                break;
        }
    }

    @Override
    public void detectText() {
        TextRecognizer recognizer = new TextRecognizer.Builder(MainActivity.this).build();
        Bitmap bitmap = ((BitmapDrawable) main_image.getDrawable()).getBitmap();
        Frame frame = new Frame.Builder().setBitmap(bitmap).build();
        SparseArray<TextBlock> sparseArray = recognizer.detect(frame);
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < sparseArray.size(); i++) {
            TextBlock tx = sparseArray.get(i);
            String str = tx.getValue();
            stringBuilder.append(str);
        }
        image_details.setText(stringBuilder);
        lookExpectedWords();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_api_activty:
                goApiActivty();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goApiActivty() {
        Intent intent = new Intent(this, VisionApiActivity.class);
        startActivity(intent);

    }
}


