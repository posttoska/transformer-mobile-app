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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    ImageView imageView;

    FrameLayout imageLayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // create image view obj
        imageView = findViewById(R.id.imageView);

        // create overlay obj
        imageLayer = findViewById(R.id.imageLayer);

        // TODO ask for permission of camera upon first launch of application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA)  == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED){
                String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                requestPermissions(permission, 112);
            }
        }

        // TODO chose image from gallery
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE);
            }
        });

        //TODO capture image using camera
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
                return false;
            }
        });
    };

    Uri image_uri;
    List detections;
    // capture image from gallery
    private static final int RESULT_LOAD_IMAGE = 123;
    // capture image by camera
    public static final int IMAGE_CAPTURE_CODE = 654;

    // TODO opens camera so that user can capture image
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

        // check response
        if (resultCode == RESULT_OK) {

            // get image uri if it was captured from gallery
            if (requestCode == RESULT_LOAD_IMAGE){
                image_uri = data.getData();
            }

            // convert image into bitmap
            Bitmap inputBmp = uriToBitmap(image_uri);
            // display given image
            imageView.setImageBitmap(inputBmp);

            // solution source: https://stackoverflow.com/questions/3425906/creating-temporary-files-in-android
            // context being the Activity pointer
            File outputDir = getCacheDir();
            // create temp file
            File tempFile = null;
            tempFile = new File(outputDir, "upload_img.png");

            // make input stream
            try {
                InputStream inStream = getContentResolver().openInputStream(image_uri);
                FileOutputStream outStream = new FileOutputStream(tempFile);

                IOUtils.copy(inStream, outStream);

                inStream.close();
                outStream.close();

            } catch (IOException e) {
                throw new RuntimeException(e);
            };

            // send to backend a file
            sendToBackend(tempFile);
        }
    }

    private void sendToBackend(File file) {

        // create request body
        RequestBody requestBody = RequestBody.create(MediaType.parse("image/*"), file);
        // create multipart for requestBody part
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
        // getting retrofit connection and api
        Retrofit retrofit = NetworkClient.getRetrofit();
        UploadAPI uploadAPI = retrofit.create(UploadAPI.class);


        // create a call
        Call call = uploadAPI.postImage(part);

        // call a call
        call.enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {


                if (response.isSuccessful()) {
                    // handle the response
                    // Log.d("BackResponse", response.body().toString());

                    // insert detections
                    detections = (List) response.body();
                    // logging
                    Log.d("BackResponseDetections", detections.toString());

                    // create mask
                    createMaskOverlay(detections);

                } else {
                    // handle error
                    Log.e("BackResponse", "Something went wrong");
                }
            }

            @Override
            public void onFailure(Call call, Throwable t) {
                throw new RuntimeException(t);
            }
        });

    };

    private void createMaskOverlay(List detections_list) {


        List<List<Double>> boxes1 = (List<List<Double>>) detections_list.get(0);
        List<Number> scores1 = (List<Number>) detections_list.get(1);
        List<Number> labels1 = (List<Number>) detections_list.get(2);

        List<String> labels_names = new ArrayList<>();
        List<List<Double>> boxes_reduced = new ArrayList<>();
        List<Float> scores_reduced = new ArrayList<>();
        List<String> labels_reduced = new ArrayList<>();
        List<List> detections_reduced = new ArrayList<>();

        if (detections_list != null && !detections_list.isEmpty()) {

            String[] names = {"background", "person", "bird", "cat",
                     "cow", "dog","horse", "sheep", "aeroplane",
                    "bicycle",  "boat", "bus", "car", "motorbike",
                    "train", "bottle", "chair", "diningtable",
                    "pottedplant", "sofa", "tvmonitor", "apple", "cup"};

            float trsh = 0.7f;

            for (int i = 0; i < labels1.size(); i++) {
                // get current index from name
                int digital_name = labels1.get(i).intValue();
                float score = scores1.get(i).floatValue();

                String real_name = names[digital_name];

                // delete all backgound items from 3 arrays
                // delete small scored items from 3 arrays
                if (!real_name.equals("background") && score > trsh) {
                    boxes_reduced.add(boxes1.get(i));
                    scores_reduced.add(scores1.get(i).floatValue());
                    labels_reduced.add(real_name);
                }
            }

        } else {
            System.out.println("The list is empty or null");
        }

//        Log.d("BackResponseDetections", boxes_reduced.toString());
//        Log.d("BackResponseDetections", scores_reduced.toString());
//        Log.d("BackResponseDetections", labels_reduced.toString());

        // append reduced detections
        detections_reduced.add(boxes_reduced);
        detections_reduced.add(scores_reduced);
        detections_reduced.add(labels_reduced);

        // create overlay instance
        MyBBoxView bboxView = new MyBBoxView(this, detections_reduced);

        // make overlay
        imageLayer.addView(bboxView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    // TODO take URI of the image and returns bitmap
    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor =
                    parcelFileDescriptor.getFileDescriptor();
            Bitmap image =
                    BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }



}
