package com.example.fitfule1;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class details extends AppCompatActivity {

    private EditText etName, etAge, etHeight, etWeight;
    private RadioGroup rgGender;
    private Spinner spinnerActivityLevel;
    private Button btnCalculateGoal;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Activity level multipliers
    private final Map<String, Double> activityFactors = new HashMap<String, Double>() {{
        put("Sedentary (Little to no exercise)", 1.2);
        put("Lightly Active (1-3 days/week)", 1.375);
        put("Moderately Active (3-5 days/week)", 1.55);
        put("Very Active (6-7 days/week)", 1.725);
        put("Extra Active (Physical job/training)", 1.9);
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI elements (matches your XML IDs exactly)
        etName = findViewById(R.id.et_name);
        etAge = findViewById(R.id.et_age);
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);
        rgGender = findViewById(R.id.rg_gender);
        spinnerActivityLevel = findViewById(R.id.spinner_activity_level);
        btnCalculateGoal = findViewById(R.id.btn_calculate_goal);

        // Setup Spinner (uses @array/activity_levels from strings.xml)
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.activity_levels,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActivityLevel.setAdapter(adapter);

        // Button click → calculate + save + go to MainActivity
        btnCalculateGoal.setOnClickListener(v -> calculateAndSaveProfile());
    }

    private void calculateAndSaveProfile() {
        // === 1. Get & validate inputs ===
        String name = etName.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(ageStr) ||
                TextUtils.isEmpty(heightStr) || TextUtils.isEmpty(weightStr)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        int age;
        double height, weight;
        try {
            age = Integer.parseInt(ageStr);
            height = Double.parseDouble(heightStr);
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter valid numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        // Realistic range validation
        if (age < 15 || age > 100) {
            Toast.makeText(this, "Age must be 15–100", Toast.LENGTH_SHORT).show();
            return;
        }
        if (height < 100 || height > 250) {
            Toast.makeText(this, "Height must be 100–250 cm", Toast.LENGTH_SHORT).show();
            return;
        }
        if (weight < 30 || weight > 300) {
            Toast.makeText(this, "Weight must be 30–300 kg", Toast.LENGTH_SHORT).show();
            return;
        }

        // === 2. Get Gender ===
        int checkedId = rgGender.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, "Please select gender", Toast.LENGTH_SHORT).show();
            return;
        }
        String gender = checkedId == R.id.rb_male ? "Male" : "Female";

        // === 3. Get Activity Level ===
        String activityLevel = spinnerActivityLevel.getSelectedItem().toString();
        Double factor = activityFactors.get(activityLevel);
        if (factor == null) factor = 1.2; // fallback

        // === 4. Calculate BMR + TDEE (Mifflin-St Jeor) ===
        double bmr = gender.equals("Male")
                ? 10 * weight + 6.25 * height - 5 * age + 5
                : 10 * weight + 6.25 * height - 5 * age - 161;

        int dailyCalorieGoal = (int) Math.round(bmr * factor);

        // === 5. Save to Firestore ===
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, login.class));
            finish();
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("age", age);
        profile.put("heightCm", height);
        profile.put("weightKg", weight);
        profile.put("gender", gender);
        profile.put("activityLevel", activityLevel);
        profile.put("dailyCalorieGoal", dailyCalorieGoal);

        db.collection("users").document(currentUser.getUid())
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(details.this,
                            "Profile saved! Your goal: " + dailyCalorieGoal + " cal/day",
                            Toast.LENGTH_LONG).show();
                    goToMainActivity();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(details.this,
                            "Failed to save profile. Try again.", Toast.LENGTH_LONG).show();
                });
    }

    private void goToMainActivity() {
        Intent intent = new Intent(details.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}