package com.example.imagepicker;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.*; // OkHttp
import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import android.view.ViewGroup;
import com.example.imagepicker.BoxOverlayView;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.FileDescriptor;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    ImageView imageView;
    BoxOverlayView overlay;
    // lan ip
    private static final String BASE_URL = "http://192.168.1.223:8000";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);

        // add an overlay view
        overlay = new BoxOverlayView(this);
        overlay.setPadding(imageView.getPaddingLeft(), imageView.getPaddingTop(),
                imageView.getPaddingRight(), imageView.getPaddingBottom());
        ((ViewGroup) imageView.getParent()).addView(overlay, imageView.getLayoutParams());
        overlay.bringToFront();

        //TODO ask for permission of camera upon first launch of application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED){
                String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                requestPermissions(permission, 112);
            }
        }

        //TODO chose image from gallery
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE);
            }
        });

        //TODO captue image using camera
        imageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_DENIED){
                        String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                        requestPermissions(permission, 112);
                    }
                    else {
                        openCamera();
                    }
                }

                else {
                    openCamera();
                }
                return true;
            }
        });
    }

    Uri image_uri;
    private static final int RESULT_LOAD_IMAGE = 123;
    public static final int IMAGE_CAPTURE_CODE = 654;
    //TODO opens camera so that user can capture image
    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK) {
            com.bumptech.glide.Glide.with(this).load(image_uri).into(imageView);
            sendToBackend(image_uri);
        } else if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            image_uri = data.getData();
            com.bumptech.glide.Glide.with(this).load(image_uri).into(imageView);
            sendToBackend(image_uri);
        }
    }

    private void sendToBackend(Uri uri) {
        try {
            byte[] bytes = readAllBytes(uri);

            // multipart form-data with field name "file"
            RequestBody fileBody = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
            MultipartBody reqBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "image.jpg", fileBody)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/predict")
                    .post(reqBody)
                    .build();

            OkHttpClient client = new OkHttpClient();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful() || body == null) return;
                        String json = body.string();

                        JSONObject root = new JSONObject(json);
                        JSONObject preds = root.getJSONObject("predictions");
                        JSONArray boxes  = preds.getJSONArray("boxes");
                        JSONArray scores = preds.getJSONArray("scores");
                        JSONArray names  = preds.getJSONArray("label_names");

                        List<BoxOverlayView.Detection> dets = new ArrayList<>();
                        for (int i = 0; i < boxes.length(); i++) {
                            JSONArray b = boxes.getJSONArray(i);
                            float x1 = (float) b.getDouble(0);
                            float y1 = (float) b.getDouble(1);
                            float x2 = (float) b.getDouble(2);
                            float y2 = (float) b.getDouble(3);
                            float sc = (float) scores.getDouble(i);
                            String lb = names.getString(i);
                            dets.add(new BoxOverlayView.Detection(x1, y1, x2, y2, lb, sc));
                        }

                        // original image size (server used these dimensions)
                        int[] wh = getImageSize(uri);
                        int origW = wh[0], origH = wh[1];

                        runOnUiThread(() -> overlay.setDetections(dets, origW, origH));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private int[] getImageSize(Uri uri) throws Exception {
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(is, null, opts);
        }
        return new int[]{opts.outWidth, opts.outHeight};
    }

}

