package com.example.fitfule1;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser user;

    // UI
    private TextView tvWelcome, tvEmail, tvDailyGoal, tvProgress;
    private ProgressBar progressBarGoal;
    private CardView cardTodayMeals, cardHistory;

    // Data
    private int dailyGoal = 0;
    private final Map<String, Integer> mealCalories = new HashMap<>();
    private final Map<String, View> mealViews = new HashMap<>();

    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private String todayDate;

    private final String[] MEALS = {"breakfast", "lunch", "snack", "dinner"};
    private final String[] MEAL_NAMES = {"Breakfast", "Lunch", "Snack", "Dinner"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Offline persistence
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);

        // Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI binding
        tvWelcome = findViewById(R.id.tv_welcome);
        tvEmail = findViewById(R.id.tv_email);
        tvDailyGoal = findViewById(R.id.tv_daily_goal);
        tvProgress = findViewById(R.id.tv_progress);
        progressBarGoal = findViewById(R.id.progress_bar_goal);
        cardTodayMeals = findViewById(R.id.card_today_meals);
        cardHistory = findViewById(R.id.card_history);

        findViewById(R.id.logout).setOnClickListener(v -> logout());

        // Card clicks
        cardTodayMeals.setOnClickListener(v -> startActivity(new Intent(this, MealLogActivity.class)));
        cardHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        todayDate = keyFormat.format(new Date());
        initMealViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        user = auth.getCurrentUser();
        if (user == null) {
            redirectToLogin();
            return;
        }
        loadUserProfile();
        loadAllMeals();
    }

    private void initMealViews() {
        int[] mealIds = {
                R.id.meal_breakfast, R.id.meal_lunch,
                R.id.meal_snack, R.id.meal_dinner
        };

        for (int i = 0; i < MEALS.length; i++) {
            View mealView = findViewById(mealIds[i]);
            String mealKey = MEALS[i];
            String mealName = MEAL_NAMES[i];

            TextView tvTitle = mealView.findViewById(R.id.tv_meal_title);
            TextView tvProgress = mealView.findViewById(R.id.tv_meal_progress);
            ProgressBar pb = mealView.findViewById(R.id.progress_bar_meal);
            EditText etFood = mealView.findViewById(R.id.et_food);
            EditText etCal = mealView.findViewById(R.id.et_calories);
            Button btnAdd = mealView.findViewById(R.id.btn_add);

            tvTitle.setText(mealName);
            mealViews.put(mealKey, mealView);

            btnAdd.setOnClickListener(v -> addToMeal(mealKey, etFood, etCal, tvProgress, pb));
        }
    }

    private void addToMeal(String meal, EditText etFood, EditText etCal, TextView tvProgress, ProgressBar pb) {
        String food = etFood.getText().toString().trim();
        String calStr = etCal.getText().toString().trim();

        if (TextUtils.isEmpty(food) || TextUtils.isEmpty(calStr)) {
            Toast.makeText(this, "Enter food and calories", Toast.LENGTH_SHORT).show();
            return;
        }

        int calories;
        try {
            calories = Integer.parseInt(calStr);
            if (calories <= 0) throw new Exception();
        } catch (Exception e) {
            Toast.makeText(this, "Enter valid calories", Toast.LENGTH_SHORT).show();
            return;
        }

        int previous = mealCalories.getOrDefault(meal, 0);
        int newTotal = previous + calories;

        // Save to Firestore
        saveMealToFirestore(meal, food, calories, newTotal, previous);

        // Update UI
        mealCalories.put(meal, newTotal);
        updateMealUI(meal, newTotal, tvProgress, pb);
        updateTotalProgress();

        etFood.setText("");
        etCal.setText("");
        Toast.makeText(this, food + " +" + calories + " cal", Toast.LENGTH_SHORT).show();
    }

    private void saveMealToFirestore(String meal, String foodName, int calories, int newTotal, int previous) {
        String uid = user.getUid();

        // Save individual food entry
        Map<String, Object> foodEntry = new HashMap<>();
        foodEntry.put("name", foodName);
        foodEntry.put("calories", calories);
        foodEntry.put("timestamp", com.google.firebase.Timestamp.now());

        db.collection("users").document(uid)
                .collection("daily_logs").document(todayDate)
                .collection("meals").document(meal)
                .collection("foods")
                .add(foodEntry)
                .addOnSuccessListener(documentReference -> {
                    // Update meal total
                    Map<String, Object> mealData = new HashMap<>();
                    mealData.put("calories", newTotal);
                    mealData.put("lastUpdated", com.google.firebase.Timestamp.now());

                    db.collection("users").document(uid)
                            .collection("daily_logs").document(todayDate)
                            .collection("meals").document(meal)
                            .set(mealData)
                            .addOnSuccessListener(aVoid -> {
                                // Update daily total
                                db.collection("users").document(uid)
                                        .collection("daily_logs").document(todayDate)
                                        .set(Map.of("totalCalories", FieldValue.increment(calories)),
                                                SetOptions.merge())
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Failed to update total", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to save meal", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save food", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateMealUI(String meal, int calories, TextView tv, ProgressBar pb) {
        int suggested = dailyGoal > 0 ? dailyGoal / 4 : 600;
        tv.setText(calories + " / " + suggested + " cal");
        int prog = suggested > 0 ? (int) ((calories * 100.0) / suggested) : 0;
        prog = Math.min(prog, 150);
        pb.setProgress(prog);
    }

    private void loadUserProfile() {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String email = doc.getString("email");
                        Long goal = doc.getLong("dailyCalorieGoal");
                        dailyGoal = goal != null ? goal.intValue() : 2400;

                        tvWelcome.setText(name != null ? "Welcome " + name + "!" : "Welcome!");
                        tvEmail.setText(email != null ? email : "N/A");
                        tvDailyGoal.setText("Daily Goal: " + dailyGoal + " cal");
                    } else {
                        tvWelcome.setText("Welcome!");
                        tvDailyGoal.setText("Daily Goal: Not set");
                        dailyGoal = 2400;
                    }
                    updateTotalProgress();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Failed to load profile", Toast.LENGTH_SHORT).show());
    }

    private void loadAllMeals() {
        mealCalories.clear();
        for (String meal : MEALS) {
            db.collection("users").document(user.getUid())
                    .collection("daily_logs").document(todayDate)
                    .collection("meals").document(meal)
                    .get()
                    .addOnSuccessListener(doc -> {
                        int mealCal = 0;
                        if (doc.exists()) {
                            Long caloriesLong = doc.getLong("calories");
                            if (caloriesLong != null) mealCal = caloriesLong.intValue();
                        }
                        mealCalories.put(meal, mealCal);

                        View v = mealViews.get(meal);
                        if (v != null) {
                            TextView tv = v.findViewById(R.id.tv_meal_progress);
                            ProgressBar pb = v.findViewById(R.id.progress_bar_meal);
                            updateMealUI(meal, mealCal, tv, pb);
                        }
                        updateTotalProgress();
                    })
                    .addOnFailureListener(e -> {
                        mealCalories.put(meal, 0);
                        updateTotalProgress();
                    });
        }
    }

    private void updateTotalProgress() {
        int total = mealCalories.values().stream().mapToInt(Integer::intValue).sum();
        tvProgress.setText(total + " / " + dailyGoal + " cal");
        int prog = dailyGoal > 0 ? (int) ((total * 100.0) / dailyGoal) : 0;
        prog = Math.min(prog, 100);
        progressBarGoal.setProgress(prog);
    }

    private void logout() {
        auth.signOut();
        redirectToLogin();
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, login.class));
        finish();
    }
}