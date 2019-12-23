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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.emrhmrc.akampusodevim.R;
import com.emrhmrc.akampusodevim.helper.ImageHelper;
import com.emrhmrc.akampusodevim.helper.StringHelper;
import com.emrhmrc.akampusodevim.util.PackageManagerUtils;
import com.emrhmrc.akampusodevim.util.PermissionUtils;
import com.emrhmrc.sweetdialoglib.DialogCreater;
import com.emrhmrc.sweetdialoglib.SweetAlertDialog;
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
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.emrhmrc.akampusodevim.helper.Constants.ANDROID_CERT_HEADER;
import static com.emrhmrc.akampusodevim.helper.Constants.ANDROID_PACKAGE_HEADER;
import static com.emrhmrc.akampusodevim.helper.Constants.CLOUD_VISION_API_KEY;
import static com.emrhmrc.akampusodevim.helper.Constants.FILE_NAME;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int MAX_DIMENSION = 1200;

    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    private static final int CAMERA_PERMISSIONS_REQUEST = 2;
    private static final int CAMERA_IMAGE_REQUEST = 3;
    private static final int RESULT_CROP = 4;

    @BindView(R.id.image_details)
    TextView image_details;
    @BindView(R.id.main_image)
    ImageView main_image;
    @BindView(R.id.fab)
    FloatingActionButton fab;
    private SweetAlertDialog loadinDialog;
    private List<String> expectedStrings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initialize();
        loadExpectedStrings();
    }

    private void loadExpectedStrings() {
        if (expectedStrings == null)
            expectedStrings = new ArrayList<>();
        expectedStrings.add("erişim");
        expectedStrings.add("ile");
        expectedStrings.add("ver");
    }

    private void initialize() {

        expectedStrings = new ArrayList<>();
    }

    private void openPickImageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setMessage(R.string.dialog_select_prompt)
                .setPositiveButton(R.string.dialog_select_gallery, (dialog, which) -> startGalleryChooser())
                .setNegativeButton(R.string.dialog_select_camera, (dialog, which) -> startCamera());
        builder.create().show();
    }

    public void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,
                    getResources().getString(R.string.dialog_select_prompt)),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    public void startCamera() {
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

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap = ImageHelper.scaleBitmapDown(
                        MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                        MAX_DIMENSION);
                callCloudVision(bitmap);
                main_image.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.e(TAG, "Image picking failed because " + e.getMessage());
                DialogCreater.errorDialog(this, getResources().getString(R.string.image_picker_error));
            }
        } else {
            Log.i(TAG, "Image picker gave us a null image.");
            DialogCreater.errorDialog(this, getResources().getString(R.string.image_picker_error));
        }
    }

    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);
                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);
                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);
                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);


            // add the features we want
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature textdetect = new Feature();
                textdetect.setType("TEXT_DETECTION");
                add(textdetect);
            }});

            // Add the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        annotateRequest.setDisableGZipContent(true);
        Log.e(TAG, "created Cloud Vision request object, sending request");
        return annotateRequest;
    }

    private void callCloudVision(final Bitmap bitmap) {

        try {
            AsyncTask<Object, Void, String> textDetectionTask =
                    new TextDetectionTask(MainActivity.this,
                            prepareAnnotationRequest(bitmap));
            textDetectionTask.execute();
        } catch (IOException e) {
            DialogCreater.errorDialog(this, "Failed: " + e.getLocalizedMessage());
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }

    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "";
        List<EntityAnnotation> annotations;
        AnnotateImageResponse annotateImageResponse = response.getResponses().get(0);
        annotations = annotateImageResponse.getTextAnnotations();
        if (annotations != null) {
            for (EntityAnnotation annotation : annotations) {
                message += annotation.getDescription() + " ";

            }
        } else {
            message += "nothing";
        }

        return message;
    }

    private void lookExpectedWords(String foundedString) {

        int point = 0;
        for (String item : expectedStrings
        ) {
            if (foundedString.contains(item.toLowerCase())) {
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

    private void performCrop(Uri picUri) {
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

    private class TextDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        TextDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
            loadinDialog = DialogCreater.loadingDialog(activity);
        }

        @Override
        protected String doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());

            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());

            }
            return "error";
        }

        protected void onPostExecute(String result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                if (!result.equals("error")) {
                    result = result.replace("\n", "").replace("\r", "").toLowerCase();
                    image_details.setText(result);
                    lookExpectedWords(result);
                } else {
                    DialogCreater.errorDialog(activity, "VisionApi Error!");
                }
                loadinDialog.dismissWithAnimation();

            }
        }

    }

}
