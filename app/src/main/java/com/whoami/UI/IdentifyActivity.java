package com.whoami.UI;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;
import com.whoami.R;
import com.whoami.Utils.Constants;
import com.whoami.helpers.Auth;
import com.whoami.helpers.GsonHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import needle.Needle;
import needle.UiRelatedTask;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.whoami.Utils.Constants.*;
import static com.whoami.helpers.ImageHelper.*;

public class IdentifyActivity extends AppCompatActivity {

    private static FaceServiceClient faceServiceClient;

    ImageView imageView;
    TextView description;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identify);

        imageView = findViewById(R.id.imageView1);
        description = findViewById(R.id.descriptionInterimPage);

        faceServiceClient = Auth.getFaceServiceClient();

        takeImage();
    }

    public void takeImage() {
        final CharSequence[] items = {"Take Photo", "Choose from Library", "Cancel"};
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(IdentifyActivity.this);
        builder.setTitle("Add Photo!");
        builder.setCancelable(false);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {

                if (items[item].equals("Choose from Library")) {
                    Intent intent = new Intent(
                            Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, PICK_IMAGE_GALLERY);

                } else if (items[item].equals("Take Photo")) {
                    if (Build.VERSION.SDK_INT >= 23 &&
                            ActivityCompat.checkSelfPermission(IdentifyActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(IdentifyActivity.this, new String[]{Manifest.permission.CAMERA},
                                REQUEST_CAMERA_PERMISSION);
                    }else {
                        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                        intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, content_download_dir);
                        startActivityForResult(intent, TAKE_IMAGE);
                    }
                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                    finish(); // Goto main activity
                }
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case PICK_IMAGE_GALLERY:
                if (resultCode == RESULT_OK && data != null && data.getData() != null)
                    saveImageCopy(data.getData());
                else
                    finish();

                break;

            case TAKE_IMAGE:
                if (resultCode == RESULT_OK)
                    saveImageCopy(null);
                else
                    finish();

                break;

            case REQUEST_CAMERA_PERMISSION:
                if (resultCode == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, content_download_dir);
                    intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                    startActivityForResult(intent, TAKE_IMAGE);
                }else
                    finish();
                break;

            case START_ADD_ACTIVITY:
                if (resultCode == RESULT_OK){
                    /*
                        On successful creation,
                        Redirect to get Person and
                        Show Resulting Person
                     */
                    Toast.makeText(getApplicationContext(),"New User Created",Toast.LENGTH_LONG).show();
                    description.setText("New User Created");

                    List<Candidate> candidates = new ArrayList<>();
                    Candidate candidate = new Candidate();
                    String PID = data.getStringExtra(person_id);
                    candidate.personId = UUID.fromString(PID);
                    candidates.add(candidate);

                    getPerson(candidates);

                }else {
                    Toast.makeText(getApplicationContext(), "New User Creation Failed", Toast.LENGTH_LONG).show();
                    description.setText("New User Creation Failed");
                }
                break;
        }
    }


    /**
     * Make copy of the image
     * Resize it so that h,w < 1400
     * Set the uri for future use
     *
     * @param uri URI of the selected image
     */
    private void saveImageCopy(Uri uri){

        if (uri == null) // Null only in case of camera
            uri = file_read_uri;

        try{
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),uri);

            ExifInterface ei = new ExifInterface(uri.getPath());
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);

            Bitmap rotatedBitmap = null;
            switch(orientation) {

                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotatedBitmap = rotateImage(bitmap, 90);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotatedBitmap = rotateImage(bitmap, 180);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotatedBitmap = rotateImage(bitmap, 270);
                    break;

                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotatedBitmap = bitmap;
            }

            bitmap = rotatedBitmap;

            int h1 = bitmap.getHeight();
            int w1 = bitmap.getWidth();

            // Shrink by 25% recursively
            while (h1 > MAX_IMG_DIMEN || w1 > MAX_IMG_DIMEN) {
                bitmap = Bitmap.createScaledBitmap(bitmap, w1 * 3 / 4, h1 * 3 / 4, false);
                h1 = bitmap.getHeight();
                w1 = bitmap.getWidth();

            }

            // Re writing data
            // Not Only in case of resizing, but while viewing from Gallery
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream);
            byte[] byteArray = stream.toByteArray();

            FileOutputStream overWrite = new FileOutputStream(file_read_uri.getPath(), false);
            overWrite.write(byteArray);
            overWrite.flush();
            overWrite.close();

            imageView.setImageBitmap(bitmap);
            detectAndFrame(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    private void detectAndFrame(final Bitmap imageBitmap){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream);
        final ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());

        Needle.onBackgroundThread().execute(new UiRelatedTask<Face[]>() {
            @Override
            protected Face[] doWork(){
                Face[] result = null;

                try {
                    result = faceServiceClient.detect(
                            inputStream,
                            true,         // returnFaceId
                            true,        // returnFaceLandmarks
                            null           // returnFaceAttributes: a string like "age, gender"
                    );
                }catch (Exception e){
                    e.printStackTrace();
                }

                return result;
            }

            @Override
            protected void thenDoUiRelatedWork(Face[] faces){
                if (faces != null && faces.length > 0){
                    imageView.setImageBitmap(drawFaceRectanglesOnBitmap(imageBitmap, faces));
                    imageBitmap.recycle();
                    getIdentity(faces);
                }else {
                    Toast.makeText(IdentifyActivity.this, "No Face Detected", Toast.LENGTH_LONG).show();
                    description.setText("Face Detection Failed");
                }
            }
        });
    }

    private void getIdentity(final Face[] faces){
        Needle.onBackgroundThread().execute(new UiRelatedTask<IdentifyResult[]>() {
            @Override
            protected IdentifyResult[] doWork(){
                List<UUID> faceIds = new ArrayList<>();
                for (Face face: faces)
                    faceIds.add(face.faceId);

                IdentifyResult[] results = null;
                try {
                    results = faceServiceClient.identity(
                            person_group_id,
                            faceIds.toArray(new UUID[faceIds.size()]), // faceIDs in UUID form
                            1           // maxNumber of candidates // TODO Change max no of results
                    );
                }catch (Exception e){
                    e.printStackTrace();
                }

                return results;
            }

            @Override
            protected void thenDoUiRelatedWork(IdentifyResult[] results){
                // Check whether the guys exists
                // TODO Currently detecting only first face in a face list
                String pid = results[0].candidates.get(0).personId.toString();
                checkIfExists(faces, results, pid);
            }
        });
    }

    private void checkIfExists(final Face[] faces, final IdentifyResult[] results, final String pid){
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://westcentralus.api.cognitive.microsoft.com/face/v1.0/persongroups/mnc-person-group-id/persons")
                .get()
                .addHeader("Ocp-Apim-Subscription-Key", "2bfcc78f3d3349789848ed4bb510ec85")
                .addHeader("Content-Type", "application/json")
                .addHeader("Cache-Control", "no-cache")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean found = false;
                try {
                    JSONArray array = new JSONArray(response.body().string());
                    for (int i = 0; i < array.length() && !found; ++i) {
                        JSONObject object = (JSONObject) array.get(i);
                        if (object.getString("personId").matches(pid))
                            found = true;
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }

                if (found){
                    getPerson(results[0].candidates);
                    Log.d("### Person ID",results[0].candidates.get(0).personId+"");
                }
                else
                    Needle.onMainThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            askHimToAdd(faces[0]); // TODO Ask him to chose from the set of images
                        }
                    });
            }
        });
    }

    private void askHimToAdd(final Face face){

        // Guy's new. Add him
        new AlertDialog.Builder(IdentifyActivity.this)
                .setMessage("Sorry Didn't find this face. Why not create an identity?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(IdentifyActivity.this,AddActivity.class);

                        GsonHelper gHelper = new GsonHelper();
                        intent.putExtra(FACE,gHelper.setFace(face));
                        startActivityForResult(intent,START_ADD_ACTIVITY);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        finish(); // Go back too
                    }
                })
                .show();
    }

    private void getPerson(final List<Candidate> candidates){
        Needle.onBackgroundThread().execute(new UiRelatedTask<Person>() {
            @Override
            protected Person doWork(){

                Person result = null;
                try {
                    result = faceServiceClient.getPerson(
                            Constants.person_group_id,
                            candidates.get(0).personId // personIDs in UUID form // TODO currently approving only first candidate for review
                    );
                }catch (Exception e){
                    e.printStackTrace();
                }

                return result;
            }

            @Override
            protected void thenDoUiRelatedWork(Person person){
                // U reached here means the u got the data just display
                Snackbar
                        .make(findViewById(R.id.IdentifyActivityCanvas),person.name,Snackbar.LENGTH_LONG)
                        .show();
            }
        });
    }

    /*
     * The Algo
     .. get the image
     .. call FSC.detect() : takes IPStream and returns Face[]: contains face ids and landmarks
     .. call FSC.getPersonTrainingStatus, if not done then FSC.trainPersonGroup
     .. call FSC.identify(): takes PGrpID, FaceIDs[],scrap it out of Face[], maxCandidates. Returns PerIDs and confidences
     .. if not found, call addGuy(faceID, IPStream)
     .. call FSC.getPerson(): takes PerID, returns an Person object
     .. display details
     */
}
