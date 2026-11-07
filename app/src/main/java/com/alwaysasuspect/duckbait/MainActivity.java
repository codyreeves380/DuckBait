package com.alwaysasuspect.duckbait

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PICK_DOCX_FILE = 1;
    private static final int CREATE_DOCX_FILE = 2;
    private static final String PREFS_NAME = "saved_form_inputs";
    private static final String TAG = "Docgen";
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private EditText inputDefendant, inputCauseNo, inputDate, inputDefId;
    private EditText inputDeposit, inputDay, inputYear, inputMonth;
    private EditText inputComplianceOfficer, inputPhone, inputEmail, inputBadge;
    private EditText inputGps, inputProgram, inputTax, inputJudge;

    private Button btnSelectFile, btnGenerate;
    private TextView txtSelectedFile, txtStatus;
    private Uri selectedFileUri = null;
    private Map<String, String> pendingReplacements;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputDefendant = findViewById(R.id.input_defendant);
        inputCauseNo = findViewById(R.id.input_causeno);
        inputDate = findViewById(R.id.input_date);
        inputDefId = findViewById(R.id.input_defid);
        inputDeposit = findViewById(R.id.input_deposit);
        inputDay = findViewById(R.id.input_day);
        inputMonth = findViewById(R.id.input_month);
        inputYear = findViewById(R.id.input_year);

        inputComplianceOfficer = findViewById(R.id.input_compliance_officer);
        inputPhone = findViewById(R.id.input_phone);
        inputEmail = findViewById(R.id.input_email);
        inputBadge = findViewById(R.id.input_badge);
        inputGps = findViewById(R.id.input_gps);
        inputProgram = findViewById(R.id.input_program);
        inputTax = findViewById(R.id.input_tax);
        inputJudge = findViewById(R.id.input_judge);

        bindSavedInput(inputDefendant, findViewById(R.id.save_defendant), "defendant");
        bindSavedInput(inputCauseNo, findViewById(R.id.save_causeno), "causeno");
        bindSavedInput(inputDate, findViewById(R.id.save_date), "date");
        bindSavedInput(inputDefId, findViewById(R.id.save_defid), "defid");
        bindSavedInput(inputDeposit, findViewById(R.id.save_deposit), "deposit");
        bindSavedInput(inputDay, findViewById(R.id.save_day), "day");
        bindSavedInput(inputMonth, findViewById(R.id.save_month), "month");
        bindSavedInput(inputYear, findViewById(R.id.save_year), "year");
        bindSavedInput(inputComplianceOfficer,
                findViewById(R.id.save_compliance_officer), "compliance_officer");
        bindSavedInput(inputPhone, findViewById(R.id.save_phone), "phone");
        bindSavedInput(inputEmail, findViewById(R.id.save_email), "email");
        bindSavedInput(inputBadge, findViewById(R.id.save_badge), "badge");
        bindSavedInput(inputGps, findViewById(R.id.save_gps), "gps");
        bindSavedInput(inputProgram, findViewById(R.id.save_program), "program");
        bindSavedInput(inputTax, findViewById(R.id.save_tax), "tax");
        bindSavedInput(inputJudge, findViewById(R.id.save_judge), "judge");

        btnSelectFile = findViewById(R.id.btn_select_file);
        btnGenerate = findViewById(R.id.btn_generate);
        txtSelectedFile = findViewById(R.id.txt_selected_file);
        txtStatus = findViewById(R.id.txt_status);

        btnSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(DOCX_MIME_TYPE);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(
                        Intent.createChooser(intent, "Select Template File"),
                        PICK_DOCX_FILE);
            }
        });

        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runDocumentGeneration();
            }
        });
    }

    private void bindSavedInput(
            final EditText input,
            final CheckBox saveCheckbox,
            final String key) {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (prefs.contains(key)) {
            input.setText(prefs.getString(key, ""));
            saveCheckbox.setChecked(true);
        } else {
            saveCheckbox.setChecked(false);
        }

        saveCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                prefs.edit().putString(key, input.getText().toString()).apply();
            } else {
                prefs.edit().remove(key).apply();
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (saveCheckbox.isChecked()) {
                    prefs.edit().putString(key, s.toString()).apply();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_DOCX_FILE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            selectedFileUri = data.getData();
            txtSelectedFile.setText("Selected: " + selectedFileUri.getLastPathSegment());
            txtStatus.setText("Status: Template Loaded.");
        } else if (requestCode == CREATE_DOCX_FILE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            saveGeneratedDocument(data.getData());
        } else if (requestCode == CREATE_DOCX_FILE) {
            pendingReplacements = null;
            txtStatus.setText("Status: Save cancelled.");
        }
    }

    private static String formatThrowable(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        String stack = sw.toString();
        if (stack.length() > 2000) {
            return stack.substring(0, 2000) + "\n...[truncated]";
        }
        return stack;
    }

    private void runDocumentGeneration() {
        if (selectedFileUri == null) {
            txtStatus.setText("Status: Please select a template file first!");
            return;
        }

        Map<String, String> replacements = new HashMap<>();
        replacements.put("[[DEFENDANT]]", inputDefendant.getText().toString().trim());
        replacements.put("[[CAUSENO]]", inputCauseNo.getText().toString().trim());
        replacements.put("[[DATE]]", inputDate.getText().toString().trim());
        replacements.put("[[DEFID]]", inputDefId.getText().toString().trim());
        replacements.put("[[DEPOSIT]]", inputDeposit.getText().toString().trim());
        replacements.put("[[DAY]]", inputDay.getText().toString().trim());
        replacements.put("[[MONTH]]", inputMonth.getText().toString().trim());
        replacements.put("[[YEAR]]", inputYear.getText().toString().trim());
        replacements.put("[[CO]]", inputComplianceOfficer.getText().toString().trim());
        replacements.put("[[PHONE]]", inputPhone.getText().toString().trim());
        replacements.put("[[EMAIL]]", inputEmail.getText().toString().trim());
        replacements.put("[[BADGE]]", inputBadge.getText().toString().trim());
        replacements.put("[[GPS]]", inputGps.getText().toString().trim());
        replacements.put("[[PRO]]", inputProgram.getText().toString().trim());
        replacements.put("[[TAX]]", inputTax.getText().toString().trim());
        replacements.put("[[JUDGE]]", inputJudge.getText().toString().trim());

        if (replacements.get("[[DEFENDANT]]").isEmpty()
                || replacements.get("[[CAUSENO]]").isEmpty()) {
            txtStatus.setText("Status: Please enter Defendant and Cause No!");
            return;
        }

        pendingReplacements = replacements;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(DOCX_MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE,
                "Generated_" + System.currentTimeMillis() + ".docx");
        txtStatus.setText("Status: Choose where to save the generated document.");
        startActivityForResult(intent, CREATE_DOCX_FILE);
    }

    private void saveGeneratedDocument(Uri outputUri) {
        if (pendingReplacements == null) {
            txtStatus.setText("Status: No document is ready to save.");
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(selectedFileUri);
             OutputStream output = getContentResolver().openOutputStream(outputUri)) {
            if (input == null || output == null) {
                txtStatus.setText("Status: Could not open the template or output document.");
                return;
            }
            DocxTemplateProcessor.generate(input, output, pendingReplacements);
            txtStatus.setText("Status: Document saved successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Document generation failed", t);
            String message = "Status: Document generation failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage() + "\n" + formatThrowable(t);
            txtStatus.setText(message);
        } finally {
            pendingReplacements = null;
        }
    }
}
l