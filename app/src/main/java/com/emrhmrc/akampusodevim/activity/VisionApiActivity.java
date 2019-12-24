package com.emrhmrc.akampusodevim.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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
import android.widget.ImageView;
import android.widget.TextView;

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

public class VisionApiActivity extends BaseActivity {

    private static final String TAG = "VisionApiActivity";
    private SweetAlertDialog loadinDialog;

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
                    new TextDetectionTask(VisionApiActivity.this,
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

    @Override
    protected void onLoad() {
        setTitle("ApiSide");
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
    protected void detectText() {
        Bitmap bitmap = ((BitmapDrawable) main_image.getDrawable()).getBitmap();
        callCloudVision(bitmap);
    }


    private class TextDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<BaseActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        TextDetectionTask(BaseActivity activity, Vision.Images.Annotate annotate) {
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
            BaseActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                if (!result.equals("error")) {
                    result = result.replace("\n", "").replace("\r", "").toLowerCase();
                    image_details.setText(result);
                    lookExpectedWords();
                } else {
                    DialogCreater.errorDialog(activity, "VisionApi Error!");
                }
                loadinDialog.dismissWithAnimation();

            }
        }

    }

}