package com.example.fitfule1;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    TextInputEditText editTextEmail, editTextPassword;
    Button buttonReg;
    FirebaseAuth mAuth;
    FirebaseFirestore db;  // ✅ ADDED FIRESTORE
    ProgressBar progressBar;
    TextView textView;

    @Override
    public void onStart() {
        super.onStart();
        if (mAuth != null) {  // ✅ FIX: Check if mAuth is initialized
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // ✅ INITIALIZE FIRESTORE
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();  // ✅ ADDED

        editTextEmail = findViewById(R.id.email);
        editTextPassword = findViewById(R.id.password);
        buttonReg = findViewById(R.id.btn_register);
        progressBar = findViewById(R.id.progressBar);
        textView = findViewById(R.id.loginNow);

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), login.class);
                startActivity(intent);
                finish();
            }
        });

        buttonReg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.setVisibility(View.VISIBLE);
                String email, password;
                email = String.valueOf(editTextEmail.getText());
                password = String.valueOf(editTextPassword.getText());

                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(Register.this, "Enter email", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                if (TextUtils.isEmpty(password)) {
                    Toast.makeText(Register.this, "Enter password", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                // ✅ CREATE USER WITH EMAIL/PASSWORD
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                progressBar.setVisibility(View.GONE);
                                if (task.isSuccessful()) {
                                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                    if (firebaseUser != null) {
                                        String uid = firebaseUser.getUid();

                                        // ✅ SAVE EMAIL TO FIRESTORE
                                        Map<String, Object> userData = new HashMap<>();
                                        userData.put("email", firebaseUser.getEmail());

                                        db.collection("users").document(uid)
                                                .set(userData, SetOptions.merge())  // ✅ Merge to avoid overwriting
                                                .addOnSuccessListener(aVoid -> {
                                                    Log.d("Register", "Email saved to Firestore successfully");
                                                    Toast.makeText(Register.this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                                                    // ✅ GO TO DETAILS PAGE
                                                    Intent intent = new Intent(Register.this, details.class);
                                                    startActivity(intent);
                                                    finish();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.w("Register", "Error saving email to Firestore", e);
                                                    Toast.makeText(Register.this, "Account created, but profile save failed", Toast.LENGTH_SHORT).show();

                                                    // ✅ STILL GO TO DETAILS EVEN IF FIRESTORE FAILS
                                                    Intent intent = new Intent(Register.this, details.class);
                                                    startActivity(intent);
                                                    finish();
                                                });
                                    }
                                } else {
                                    Toast.makeText(Register.this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }
}