package com.app.offlineocr;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectionResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ObjectDetectorHelper.DetectorListener {

    private static final int RESULT_LOAD_IMAGE = 123;
    private static final String TAG = "Detect OCR";
    public static final int IMAGE_CAPTURE_CODE = 654;
    private static final int PERMISSION_CODE = 321;
    public String resultFlag;
    ImageView innerImage;
    EditText txtResult;
    Bitmap imgBitmap;

    TextView lblStatus;
    FloatingActionButton fabCamera,fabGallery;
    Button btnMeterDetect, btnExtract;
    private Uri image_uri;
    private SeekBar seekBar;
    private final float CONFIDENCE_THRESHOLD = 0.3f;

    ObjectDetectorHelper objectDetectorHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        innerImage = findViewById(R.id.imgMeter);
        txtResult = findViewById(R.id.textResult);
        btnExtract = findViewById(R.id.btnExtract);
        lblStatus=findViewById(R.id.lblStatus);
        fabCamera=findViewById(R.id.fab_Camera);
        fabGallery=findViewById(R.id.fab_gallery);
        txtResult.setEnabled(false);

        Intent intent1 = getIntent();
        String image_file_Name = intent1.getStringExtra("filename");

        Log.d(TAG,"Received File name: "+image_file_Name);

        if(image_file_Name!=null){

            String imagePath =  getOutputDirectory(getApplicationContext()).toString();

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath+"/"+image_file_Name);
            imgBitmap=bitmap;

            if(bitmap!=null){
                bitmap=rotateBitmap(bitmap,90);
                innerImage.setImageBitmap(bitmap);
            }

        }




        fabGallery.setOnClickListener(view -> {
            // Handle the click event here
            //Toast.makeText(MainActivity.this, "FloatingActionButton clicked", Toast.LENGTH_SHORT).show();
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

            startActivityForResult(galleryIntent, RESULT_LOAD_IMAGE);
        });

        fabCamera.setOnClickListener(view -> {



            Log.d(TAG, "You Clicked me");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_DENIED) {
                    String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                    requestPermissions(permission, PERMISSION_CODE);
                } else {
                    Log.d(TAG, "You Clicked me");
                    Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                    startActivity(intent);

                }
            } else {
                Log.d(TAG, "You Clicked me");
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
                //openCamera();
            }

            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivity(intent);
        });



        btnExtract.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle button 1 click
                objectDetectorHelper.setCurrentModel("offline_ocr.tflite");
                objectDetectorHelper.setupObjectDetector();
                Log.d(TAG, "offline_ocr model Selected ...");
                doInference();
            }
        });


        seekBar = findViewById(R.id.seekBar);
        seekBar.setScaleY(2.0f);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // This method is invoked when the slider position is changed
                // You can use the 'progress' variable to get the current value

                int val=seekBar.getProgress();

                float f=(float)val/100;

                lblStatus.setText("Confidence Level: " + String.valueOf(f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // This method is invoked when the user first touches the SeekBar
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                int val=seekBar.getProgress();

                float f=(float)val/100;

                lblStatus.setText("Confidence Level: " + String.valueOf(f));

                // Handle button 1 click
                objectDetectorHelper.setCurrentModel("offline_ocr.tflite");
                objectDetectorHelper.setupObjectDetector();
                objectDetectorHelper.setThreshold(f);

                Log.d(TAG, "offline_ocr model Selected ...");
                doInference();

                // This method is invoked after the user finishes moving the SeekBar
                //Toast.makeText(MainActivity.this, "Seekbar value: " + seekBar.getProgress(), Toast.LENGTH_SHORT).show();
            }
        });

        objectDetectorHelper = new ObjectDetectorHelper(0.5f, ObjectDetectorHelper.MAX_RESULTS_DEFAULT, ObjectDetectorHelper.DELEGATE_CPU, "offline_ocr.tflite", RunningMode.IMAGE, getApplicationContext(), this);


    }

    public Bitmap rotateBitmap(Bitmap sourceBitmap, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }


    private File getOutputDirectory(Context context) {
        File[] externalMediaDirs = context.getExternalMediaDirs();
        File mediaDir = externalMediaDirs.length > 0 ? externalMediaDirs[0] : null;

        if (mediaDir != null) {
            mediaDir = new File(mediaDir, context.getResources().getString(R.string.app_name));
            mediaDir.mkdirs();
        }

        return mediaDir != null && mediaDir.exists() ? mediaDir : context.getFilesDir();
    }

    private int getOrientation(Bitmap bitmap) {
        try {
            // Convert Bitmap to ByteArray to read Exif information
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            byte[] bitmapData = bos.toByteArray();

            // Use ExifInterface to read orientation
            ExifInterface exifInterface = new ExifInterface(new ByteArrayInputStream(bitmapData));
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            return orientation;
        } catch (IOException e) {
            e.printStackTrace();
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        lblStatus.setText("");

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            image_uri = data.getData();


            Bitmap bitmap = uriToBitmap(image_uri);

            imgBitmap=uriToBitmap(image_uri);

            Log.d(TAG, "Orientation: "+ getOrientation(bitmap));

            imgBitmap=rotateBitmap(imgBitmap,90);


            innerImage.setImageBitmap(imgBitmap);




            // doInference();
        }

        if (requestCode == IMAGE_CAPTURE_CODE && resultCode == RESULT_OK) {
            image_uri = data.getData();
            // doInference();
            Bitmap bitmap = uriToBitmap(image_uri);
            rotateBitmap(bitmap);
            innerImage.setImageBitmap(bitmap);
            doInference();

        }
    }

    public static Bitmap getBitmapFromImageView(ImageView imageView) {
        // Step 1: Get the Drawable from ImageView
        Drawable drawable = imageView.getDrawable();

        // Step 2: Convert the Drawable to Bitmap
        Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            // Handle invalid drawable
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(
                    drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888
            );
        }

        // Create a canvas and draw the Drawable onto the Bitmap
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    //Passing image to the model, getting the results and drawing rectangles
    public void doInference() {

//        Bitmap bitmap = uriToBitmap(image_uri);
//        bitmap=rotateBitmap(bitmap);
//        innerImage.setImageBitmap(bitmap);
//
        Bitmap mutableBmp = imgBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBmp);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((mutableBmp.getWidth() / 95));

        Paint paintText = new Paint();
        paintText.setColor(Color.BLUE);
        paintText.setTextSize(mutableBmp.getWidth() / 10);

        List<DetectionData> detectionDataList = new ArrayList<>();

        ObjectDetectorHelper.ResultBundle resultBundle = objectDetectorHelper.detectImage(imgBitmap);
        if (resultBundle != null) {
            Log.d("tryRess", "results are not null");
            List<ObjectDetectionResult> resultList = resultBundle.getResults();

            for (ObjectDetectionResult singleResult : resultList) {
                List<Detection> detectionList = singleResult.detections();
                for (Detection detection : detectionList) {
                    float confidence = 0;
                    String objectName = "";
                    for (Category category : detection.categories()) {
                        if (category.score() > confidence) {
                            confidence = category.score();
                            objectName = category.categoryName();
                        }
                    }


                    canvas.drawRect(detection.boundingBox(), paint);
                    canvas.drawText(objectName, detection.boundingBox().left, detection.boundingBox().top, paintText);
                    Log.d(TAG, objectName);
                    Log.d(TAG, String.valueOf(detection.boundingBox().left));
                    // Add detection data to the list
                    DetectionData data = new DetectionData(detection.boundingBox().left, objectName);
                    detectionDataList.add(data);

                    Collections.sort(detectionDataList, new Comparator<DetectionData>() {
                        @Override
                        public int compare(DetectionData o1, DetectionData o2) {
                            return Float.compare(o1.getLeft(), o2.getLeft());
                        }
                    });


                }

            }
            String kwh = "";

            Log.d(TAG, "Sorted Values are:......");
            // Assuming detectionDataList is already sorted
            for (DetectionData data : detectionDataList) {
                Log.d(TAG, "ObjectName: " + data.getObjectName() + ", Left: " + data.getLeft());

                if (data.getObjectName().contentEquals("kwh")) {
                    Log.d(TAG, "Kwh removed");
                } else {
                    kwh = kwh + data.getObjectName().toString();
                }

            }

            Log.d(TAG, "kwh value: " + kwh);

            txtResult.setText(kwh);

            if (kwh.isEmpty()) {
                Log.d(TAG, "No Result Found");
                lblStatus.setText("Meter not Detected");
            } else {

                if (kwh.contentEquals("reading")) {
                    resultFlag = kwh;
                }

                innerImage.setImageBitmap(mutableBmp);
            }



        } else {
            Log.d("tryRess", "results are null");
        }
    }


    //TODO rotate image if image captured on sumsong devices
    //Most phone cameras are landscape, meaning if you take the photo in portrait, the resulting photos will be rotated 90 degrees.
    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input) {
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
        }
        Log.d("tryOrientation", orientation + "");
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(orientation);
        Bitmap cropped = Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), rotationMatrix, true);
        return cropped;
    }


    //takes image uri as input and return that image in a bitmap format
    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onError(String var1, int var2) {

    }

    @Override
    public void onResults(ObjectDetectorHelper.ResultBundle var1) {

    }
}