package com.example.fitfule1;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private LinearLayout container;
    private final SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat dispFmt = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

    private int dailyGoal = 2400; // Default
    private int weeklyTotal = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        container = findViewById(R.id.history_container);

        loadUserGoalAndHistory();
    }

    private void loadUserGoalAndHistory() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Load daily goal from user profile
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getLong("dailyCalorieGoal") != null) {
                        dailyGoal = doc.getLong("dailyCalorieGoal").intValue();
                    }
                    load7DayHistory(); // Proceed after goal loaded
                })
                .addOnFailureListener(e -> load7DayHistory()); // Fallback
    }

    private void load7DayHistory() {
        container.removeAllViews();
        weeklyTotal = 0;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);

        // Add month title at top
        addMonthHeader(cal);

        for (int i = 0; i < 7; i++) {
            final int dayIndex = i; // FINAL COPY to avoid lambda capture error
            String dateKey = keyFmt.format(cal.getTime());
            String display = dispFmt.format(cal.getTime());
            final boolean isToday = (i == 6); // Today is the last day

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(uid)
                    .collection("daily_logs").document(dateKey)
                    .collection("meals")
                    .get()
                    .addOnSuccessListener(qs -> {
                        int total = 0;
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            Long c = d.getLong("calories");
                            if (c != null) total += c.intValue();
                        }
                        weeklyTotal += total;
                        addHistoryCard(display, total, isToday);
                        if (dayIndex == 6) addWeeklyTotal(); // Only on last day
                    })
                    .addOnFailureListener(e -> {
                        addHistoryCard(display, 0, isToday);
                        if (dayIndex == 6) addWeeklyTotal();
                    });

            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void addMonthHeader(Calendar cal) {
        TextView title = new TextView(this);
        title.setText(monthFmt.format(cal.getTime()));
        title.setTextSize(26);
        title.setTextColor(0xFF2E7D32);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 32);
        title.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        container.addView(title);
    }

    private void addHistoryCard(String date, int calories, boolean isToday) {
        CardView card = new CardView(this);
        card.setCardElevation(6);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(isToday ? 0xFFF5F5F5 : 0xFFFFFFFF);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ) {{
            bottomMargin = 16;
        }});

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(32, 24, 32, 24);

        // Date
        TextView tvDate = new TextView(this);
        tvDate.setText(date);
        tvDate.setTextSize(18);
        tvDate.setTextColor(isToday ? 0xFF2E7D32 : 0xFF333333);
        tvDate.setTypeface(null, android.graphics.Typeface.BOLD);
        inner.addView(tvDate);

        // Calories
        TextView tvCal = new TextView(this);
        tvCal.setText(calories + " cal");
        tvCal.setTextSize(20);
        tvCal.setTextColor(0xFF1976D2);
        tvCal.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCal.setPadding(0, 8, 0, 16);
        inner.addView(tvCal);

        // Progress Bar
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        int progress = dailyGoal > 0 ? (int) ((calories * 100.0) / dailyGoal) : 0;
        pb.setProgress(Math.min(progress, 100));
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE0E0E0));

        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 12
        );
        pbParams.setMargins(0, 8, 0, 8);
        pb.setLayoutParams(pbParams);
        inner.addView(pb);

        // Goal Text
        TextView tvGoal = new TextView(this);
        tvGoal.setText("of " + dailyGoal + " cal goal");
        tvGoal.setTextSize(14);
        tvGoal.setTextColor(0xFF666666);
        inner.addView(tvGoal);

        card.addView(inner);
        container.addView(card);
    }

    private void addWeeklyTotal() {
        CardView card = new CardView(this);
        card.setCardElevation(8);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(0xFFE8F5E9);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ) {{
            topMargin = 24;
        }});

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(android.view.Gravity.CENTER);
        inner.setPadding(32, 32, 32, 32);

        TextView tvLabel = new TextView(this);
        tvLabel.setText("Weekly Total:");
        tvLabel.setTextSize(18);
        tvLabel.setTextColor(0xFF1B5E20);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvTotal = new TextView(this);
        tvTotal.setText(weeklyTotal + " cal");
        tvTotal.setTextSize(22);
        tvTotal.setTextColor(0xFF2E7D32);
        tvTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTotal.setPadding(16, 0, 0, 0);

        inner.addView(tvLabel);
        inner.addView(tvTotal);
        card.addView(inner);
        container.addView(card);
    }
}