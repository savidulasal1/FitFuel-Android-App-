package com.example.fitfule1;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MealLogActivity extends AppCompatActivity {

    private LinearLayout container;
    private final SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat dateTitleFmt = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());

    private final Map<String, List<FoodEntry>> mealFoods = new HashMap<>();
    private String todayDate;
    private boolean isFirstDisplay = true; // Prevent duplicate display

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_log);

        container = findViewById(R.id.meal_log_container);
        todayDate = keyFmt.format(new Date());

        addDateHeader(); // Always show date first
        loadTodayMeals();
    }

    private void addDateHeader() {
        // Remove old header if exists
        if (container.getChildCount() > 0 && container.getChildAt(0) instanceof TextView) {
            container.removeViewAt(0);
        }

        TextView title = new TextView(this);
        title.setText(dateTitleFmt.format(new Date()));
        title.setTextSize(24);
        title.setTextColor(0xFF2E7D32);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 32);
        title.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        container.addView(title, 0); // Insert at top
    }

    private void loadTodayMeals() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mealFoods.clear();

        String[] meals = {"breakfast", "lunch", "snack", "dinner"};
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        int[] completed = {0}; // Track async calls

        for (String meal : meals) {
            mealFoods.put(meal, new ArrayList<>());
            db.collection("users").document(uid)
                    .collection("daily_logs").document(todayDate)
                    .collection("meals").document(meal)
                    .collection("foods")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<FoodEntry> foods = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot) {
                            FoodEntry food = new FoodEntry(
                                    doc.getId(),
                                    doc.getString("name"),
                                    doc.getLong("calories") != null ? doc.getLong("calories").intValue() : 0,
                                    meal
                            );
                            foods.add(food);
                        }
                        mealFoods.put(meal, foods);

                        completed[0]++;
                        if (completed[0] == meals.length) {
                            displayMealsOnce(); // Display only after all loaded
                        }
                    })
                    .addOnFailureListener(e -> {
                        completed[0]++;
                        if (completed[0] == meals.length) {
                            displayMealsOnce();
                        }
                    });
        }
    }

    private void displayMealsOnce() {
        if (!isFirstDisplay) return;
        isFirstDisplay = false;

        // Remove all except header
        while (container.getChildCount() > 1) {
            container.removeViewAt(1);
        }

        boolean hasData = false;
        String[] order = {"breakfast", "lunch", "snack", "dinner"};
        String[] icons = {"Breakfast", "Lunch", "Snack", "Dinner"};

        for (int i = 0; i < order.length; i++) {
            String mealKey = order[i];
            List<FoodEntry> foods = mealFoods.get(mealKey);
            if (foods != null && !foods.isEmpty()) {
                hasData = true;
                addMealSection(icons[i], mealKey, foods);
            }
        }

        if (!hasData) {
            addEmptyState("No food logged today");
        }
    }

    private void addMealSection(String icon, String meal, List<FoodEntry> foods) {
        CardView card = new CardView(this);
        card.setCardElevation(6);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(0xFFFFFFFF);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ) {{
            bottomMargin = 24;
        }});

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(24, 24, 24, 24);

        // Title
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setPadding(0, 0, 16, 0);

        TextView title = new TextView(this);
        title.setText(capitalize(meal));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF333333);

        titleRow.addView(iconView);
        titleRow.addView(title);
        inner.addView(titleRow);

        // Food Items
        int total = 0;
        for (FoodEntry food : foods) {
            total += food.calories;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(0, 16, 0, 16);
            item.setClickable(true);
            item.setOnClickListener(v -> showEditDialog(food));
            item.setOnLongClickListener(v -> {
                showDeleteDialog(food);
                return true;
            });

            TextView foodName = new TextView(this);
            foodName.setText(food.name);
            foodName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            foodName.setTextSize(16);
            foodName.setTextColor(0xFF555555);

            TextView calView = new TextView(this);
            calView.setText(food.calories + " cal");
            calView.setTextSize(16);
            calView.setTextColor(0xFF1976D2);
            calView.setTypeface(null, android.graphics.Typeface.BOLD);

            item.addView(foodName);
            item.addView(calView);
            inner.addView(item);
        }

        // Total
        TextView totalView = new TextView(this);
        totalView.setText("Total: " + total + " cal");
        totalView.setTextSize(16);
        totalView.setTextColor(0xFF2E7D32);
        totalView.setTypeface(null, android.graphics.Typeface.BOLD);
        totalView.setPadding(0, 16, 0, 0);
        inner.addView(totalView);

        card.addView(inner);
        container.addView(card);
    }

    private void showEditDialog(FoodEntry food) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_food, null);
        builder.setView(dialogView);

        TextInputEditText etName = dialogView.findViewById(R.id.et_food_name);
        TextInputEditText etCal = dialogView.findViewById(R.id.et_food_calories);

        etName.setText(food.name);
        etCal.setText(String.valueOf(food.calories));

        builder.setTitle("Edit Food")
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String calStr = etCal.getText().toString().trim();

                    if (newName.isEmpty() || calStr.isEmpty()) {
                        Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int newCal;
                    try {
                        newCal = Integer.parseInt(calStr);
                        if (newCal <= 0) throw new Exception();
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid calories", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateFoodInFirestore(food, newName, newCal);
                })
                .setNegativeButton("Cancel", null);

        builder.show();
    }

    private void showDeleteDialog(FoodEntry food) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Food")
                .setMessage("Delete \"" + food.name + "\" (" + food.calories + " cal)?")
                .setPositiveButton("Delete", (dialog, which) -> deleteFoodFromFirestore(food))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateFoodInFirestore(FoodEntry food, String newName, int newCal) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> update = new HashMap<>();
        update.put("name", newName);
        update.put("calories", newCal);

        db.collection("users").document(uid)
                .collection("daily_logs").document(todayDate)
                .collection("meals").document(food.meal)
                .collection("foods").document(food.docId)
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    int diff = newCal - food.calories;
                    updateMealTotal(food.meal, diff);
                    Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
    }

    private void deleteFoodFromFirestore(FoodEntry food) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid)
                .collection("daily_logs").document(todayDate)
                .collection("meals").document(food.meal)
                .collection("foods").document(food.docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    updateMealTotal(food.meal, -food.calories);
                    Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show());
    }

    private void updateMealTotal(String meal, int calorieChange) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid)
                .collection("daily_logs").document(todayDate)
                .collection("meals").document(meal)
                .update("calories", com.google.firebase.firestore.FieldValue.increment(calorieChange));

        db.collection("users").document(uid)
                .collection("daily_logs").document(todayDate)
                .update("totalCalories", com.google.firebase.firestore.FieldValue.increment(calorieChange));
    }

    private void refreshData() {
        isFirstDisplay = true;
        loadTodayMeals();
    }

    private void addEmptyState(String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(18);
        tv.setTextColor(0xFF999999);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, 64, 0, 64);
        container.addView(tv);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static class FoodEntry {
        String docId, name, meal;
        int calories;

        FoodEntry(String docId, String name, int calories, String meal) {
            this.docId = docId;
            this.name = name;
            this.calories = calories;
            this.meal = meal;
        }
    }
}