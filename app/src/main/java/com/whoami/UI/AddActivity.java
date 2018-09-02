package com.whoami.UI;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.AddPersistedFaceResult;
import com.microsoft.projectoxford.face.contract.CreatePersonResult;
import com.microsoft.projectoxford.face.contract.Face;
import com.whoami.R;
import com.whoami.helpers.Auth;
import com.whoami.helpers.GsonHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import butterknife.BindView;
import butterknife.ButterKnife;
import needle.Needle;
import needle.UiRelatedTask;

import static com.whoami.Utils.Constants.*;
import static com.whoami.helpers.ImageHelper.file_read_uri;

public class AddActivity extends AppCompatActivity {

    private static Face face;
    private static ByteArrayOutputStream outputStream;
    private FaceServiceClient faceServiceClient;
    private String studentName;

    @BindView(R.id.EditTextAddActivity)    EditText name;
    @BindView(R.id.ImageButtonAddActivity)    Button submitButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);
        ButterKnife.bind(this);

        faceServiceClient = Auth.getFaceServiceClient();

        GsonHelper gHelper = new GsonHelper();
        Intent intent = getIntent();
        face = gHelper.getFace(intent.getStringExtra(FACE));

        // Get the image
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), file_read_uri);
            outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream);

        }catch (Exception e){e.printStackTrace();}

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (name.getText().toString().matches(""))
                    name.setError("Name can't be empty");
                else{
                    studentName = name.getText().toString();
                    createPerson();
                }
            }
        });

    }

    private void createPerson(){

        Needle.onBackgroundThread().execute(new UiRelatedTask<CreatePersonResult>() {
            @Override
            protected CreatePersonResult doWork(){
                CreatePersonResult person = null;
                try {
                    person = faceServiceClient.createPerson(
                            person_group_id,
                            studentName,         // person name
                            studentName       // person data
                    );
                }catch (Exception e){
                    e.printStackTrace();
                }

                return person;
            }

            @Override
            protected void thenDoUiRelatedWork(CreatePersonResult person){
                if (person != null)
                   addPersonFace(person);
                else
                    Toast.makeText(AddActivity.this, "Create Person Returned Null", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addPersonFace(final CreatePersonResult person){
        final ProgressDialog progressDialog = new ProgressDialog(AddActivity.this);
        progressDialog.setMessage("Uploading Image");
        progressDialog.show();
        progressDialog.setCancelable(false);


        Needle.onBackgroundThread().execute(new UiRelatedTask<AddPersistedFaceResult>() {
            @Override
            protected AddPersistedFaceResult doWork(){
                AddPersistedFaceResult result = null;
                try {
                    result = faceServiceClient.addPersonFace(
                            person_group_id,
                            person.personId,
                            new ByteArrayInputStream(outputStream.toByteArray()),
                            studentName, // Person data
                            face.faceRectangle
                    );

                    faceServiceClient.trainPersonGroup(person_group_id); // This is important

                }catch (Exception e){
                    e.printStackTrace();
                }

                return result;
            }

            @Override
            protected void thenDoUiRelatedWork(AddPersistedFaceResult result){
                Intent intent = new Intent();

                // Redirect to Identify and then to Results activity
                if (result != null) {
                    Toast.makeText(AddActivity.this, "Person Face Added", Toast.LENGTH_LONG).show();
                    intent.putExtra(person_id,person.personId.toString());
                    setResult(RESULT_OK, intent);

                    Log.d("### Persisted Face ID",result.persistedFaceId+"");
                    Log.d("### Person ID",person.personId+"");
                }
                else {
                    setResult(RESULT_CANCELED, intent);
                    Toast.makeText(AddActivity.this, "Add Person Face Returned Null", Toast.LENGTH_LONG).show();
                }

                progressDialog.dismiss();
                finish();
            }
        });
    }
}

/*
 * The Algorithm
    .. u hv the image + FaceID + faceRect, get uData from him using adhar
    .. PersonResult FSC.createPerson(PGrpID, uName, uData). Get PerID from it
    .. Train Model too
    .. FSC.addPersonFace(PGrpID, PerID, IPStream, uData, faceRect)
    .. return
    .. [Later: Phone OTP Verification to add more pics]
 */
