package com.packtpub.masteringopencvandroid.featureapp;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;

public class Camera extends Activity {

    public final static String TAG = "FeatureApp::Camera";

    private final int SELECT_PHOTO = 0;
    private final int READ_PERMISSION = 1;

    private Mat     mOriginalMat;
    private Bitmap  mCurrentBitmap;     // bitmap to be applied with different algorithm
    private Bitmap  mOriginalBitmap;    // store original bitmap for compare
    private Uri     mSelectedImage;     // uri of selected image

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            Log.d(TAG, "onManagerConnected is called");
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("OpenCV Status", "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate is called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Initializing OpenCV Package Manager
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu
        getMenuInflater().inflate(R.menu.filename, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.open_gallery) {
            Intent intent = new Intent(Intent.ACTION_PICK, Uri.parse("content://media/internal/images/media"));
            startActivityForResult(intent, SELECT_PHOTO);
        }

        return super.onOptionsItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == SELECT_PHOTO && resultCode == RESULT_OK && intent != null) {

            // Get URI selected image
            mSelectedImage = intent.getData();

            // Adding checking for permission
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE);

            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "Permission is not allowed. Request permission");

                ActivityCompat.requestPermissions(this, new String[]
                        {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION);

            } else {

                // TODO: Handle for the case permission is granted
                // This is for the case, user select another image

                //mCurrentBitmap = mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, false);
                mCurrentBitmap = rotateImage(mSelectedImage);

                // Update view
                loadImageToImageView();

            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case READ_PERMISSION: {
                // If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission is granted");

                    //mCurrentBitmap = mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    mCurrentBitmap = rotateImage(mSelectedImage);

                    // Update view
                    loadImageToImageView();

                } else {
                    Log.d(TAG, "Permission is Not granted");
                }
            }
        }
    }

    private Bitmap rotateImage(Uri selectedImage) {
        String filePath = getFilePath(selectedImage);

        // To speed up loading of image
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;

        Bitmap temp = BitmapFactory.decodeFile(filePath, options);


        // Get orientation information
        int orientation = 0;

        try {
            ExifInterface imgParams = new ExifInterface(filePath);
            orientation = imgParams.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);

        } catch (IOException e) {
            e.printStackTrace();
        }

        //Rotating the image to get the correct orientation
        Matrix rotate90 = new Matrix();
        rotate90.postRotate(orientation);

        // TODO: This function has side effect. Please fix it.
        mOriginalBitmap = rotateBitmap(temp, orientation);

        //Convert Bitmap to Mat
        Bitmap tempBitmap = mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        mOriginalMat = new Mat(tempBitmap.getHeight(), tempBitmap.getWidth(), CvType.CV_8U);
        Utils.bitmapToMat(tempBitmap, mOriginalMat);

        return mOriginalBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    public String getFilePath(Uri img) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(img, filePathColumn,
                null, null, null);
        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String filePath = cursor.getString(columnIndex);
        cursor.close();

        return filePath;
    }

    private void loadImageToImageView() {
        ImageView imgView = (ImageView) findViewById(R.id.image_view);
        imgView.setImageBitmap(mCurrentBitmap);
    }

    //Function to rotate bitmap according to image parameters
    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }

    }

}
