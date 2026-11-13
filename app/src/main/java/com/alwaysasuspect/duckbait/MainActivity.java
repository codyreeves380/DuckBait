package com.alwaysasuspect.duckbait;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int CREATE_DOCX_FILE = 2;
    private static final String PREFS_NAME = "saved_form_inputs";
    private static final String TAG = "Docgen";
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private EditText inputDefendant, inputCauseNo, inputDate, inputDefId;
    private EditText inputDeposit, inputDay, inputYear, inputMonth;
    private EditText inputComplianceOfficer, inputPhone, inputEmail, inputBadge;
    private EditText inputGps, inputProgram, inputTax, inputJudge;
    private TextView txtStatus;
    private String selectedTemplateAsset;
    private String selectedDocumentName;
    private Map<String, String> pendingReplacements;
    private final Map<String, List<TemplateChoice>> templatesByState = new HashMap<>();

    private static final class TemplateChoice {
        final String name;
        final String assetPath;
        TemplateChoice(String name, String assetPath) {
            this.name = name;
            this.assetPath = assetPath;
        }
        @Override public String toString() { return name; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showStartScreen();
    }

    private void showStartScreen() {
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_open_pts).setOnClickListener(v -> showDocumentSelection());
    }

    private void showDocumentSelection() {
        setContentView(R.layout.activity_document_selection);
        Spinner stateSpinner = findViewById(R.id.spinner_state);
        Spinner countySpinner = findViewById(R.id.spinner_county);
        Button next = findViewById(R.id.btn_selection_next);
        TextView status = findViewById(R.id.txt_selection_status);
        TextView catalogHint = findViewById(R.id.txt_catalog_hint);
        List<String> states = loadCatalog();
        if (states.isEmpty()) {
            states.add("No states added yet");
            status.setText("The preset catalog could not be loaded or contains no states. Please update the app to the latest build.");
            catalogHint.setVisibility(View.VISIBLE);
        } else {
            catalogHint.setVisibility(View.GONE);
            status.setText("Choose a state, then choose the county document.");
        }
        stateSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, states));
        stateSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                String state = states.get(position);
                List<TemplateChoice> counties = templatesByState.get(state);
                List<String> labels = new ArrayList<>();
                if (counties != null) for (TemplateChoice choice : counties) labels.add(choice.name);
                if (labels.isEmpty()) labels.add("No documents available");
                countySpinner.setAdapter(new ArrayAdapter<>(MainActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, labels));
                boolean hasDocuments = counties != null && !counties.isEmpty();
                countySpinner.setEnabled(hasDocuments);
                next.setEnabled(false);
                selectedTemplateAsset = null;
                selectedDocumentName = null;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                next.setEnabled(false);
            }
        });
        countySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                String state = (String) stateSpinner.getSelectedItem();
                List<TemplateChoice> counties = templatesByState.get(state);
                if (counties != null && position < counties.size()) {
                    TemplateChoice choice = counties.get(position);
                    selectedTemplateAsset = choice.assetPath;
                    selectedDocumentName = choice.name;
                    next.setEnabled(true);
                } else {
                    selectedTemplateAsset = null;
                    selectedDocumentName = null;
                    next.setEnabled(false);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedTemplateAsset = null;
                selectedDocumentName = null;
                next.setEnabled(false);
            }
        });
        findViewById(R.id.btn_selection_back).setOnClickListener(v -> showStartScreen());
        next.setOnClickListener(v -> {
            if (selectedTemplateAsset != null) showDataEntryScreen();
        });
    }

    private List<String> loadCatalog() {
        templatesByState.clear();
        List<String> states = new ArrayList<>();
        try (InputStream input = getAssets().open("pts_templates/catalog.json")) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
            }
            JSONObject root = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            JSONArray stateArray = root.optJSONArray("states");
            if (stateArray == null) return states;
            for (int i = 0; i < stateArray.length(); i++) {
                JSONObject stateObject = stateArray.getJSONObject(i);
                String stateName = stateObject.optString("name", "").trim();
                if (stateName.isEmpty()) continue;
                JSONArray countyArray = stateObject.optJSONArray("counties");
                List<TemplateChoice> choices = new ArrayList<>();
                if (countyArray != null) {
                    for (int j = 0; j < countyArray.length(); j++) {
                        JSONObject county = countyArray.getJSONObject(j);
                        String name = county.optString("name", "").trim();
                        String path = county.optString("template", "").trim();
                        if (!name.isEmpty() && path.startsWith("pts_templates/")
                                && path.endsWith(".docx")) {
                            choices.add(new TemplateChoice(name, path));
                        }
                    }
                }
                templatesByState.put(stateName, choices);
                states.add(stateName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read PTS template catalog", e);
        }
        return states;
    }

    private void showDataEntryScreen() {
        setContentView(R.layout.activity_data_entry);
        bindDataEntryFields();
        findViewById(R.id.btn_data_back).setOnClickListener(v -> showDocumentSelection());
        findViewById(R.id.btn_data_next).setOnClickListener(v -> showFinalScreen());
    }

    private void bindDataEntryFields() {
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
        bindSavedInput(inputComplianceOfficer, findViewById(R.id.save_compliance_officer), "compliance_officer");
        bindSavedInput(inputPhone, findViewById(R.id.save_phone), "phone");
        bindSavedInput(inputEmail, findViewById(R.id.save_email), "email");
        bindSavedInput(inputBadge, findViewById(R.id.save_badge), "badge");
        bindSavedInput(inputGps, findViewById(R.id.save_gps), "gps");
        bindSavedInput(inputProgram, findViewById(R.id.save_program), "program");
        bindSavedInput(inputTax, findViewById(R.id.save_tax), "tax");
        bindSavedInput(inputJudge, findViewById(R.id.save_judge), "judge");
    }

    private void bindSavedInput(final EditText input, final CheckBox saveCheckbox, final String key) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.contains(key)) {
            input.setText(prefs.getString(key, ""));
            saveCheckbox.setChecked(true);
        } else {
            saveCheckbox.setChecked(false);
        }
        saveCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) prefs.edit().putString(key, input.getText().toString()).apply();
            else prefs.edit().remove(key).apply();
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (saveCheckbox.isChecked()) {
                    prefs.edit().putString(key, s.toString()).apply();
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void showFinalScreen() {
        setContentView(R.layout.activity_final);
        ((TextView) findViewById(R.id.txt_final_document))
                .setText("Selected preset: " + selectedDocumentName);
        findViewById(R.id.btn_final_back).setOnClickListener(v -> showDataEntryScreen());
        findViewById(R.id.btn_generate).setOnClickListener(v -> runDocumentGeneration());
    }

    private static String formatThrowable(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String stack = sw.toString();
        return stack.length() > 2000 ? stack.substring(0, 2000) + "\n...[truncated]" : stack;
    }

    private void runDocumentGeneration() {
        if (selectedTemplateAsset == null || selectedDocumentName == null) {
            TextView status = findViewById(R.id.txt_final_status);
            if (status != null) status.setText("No preset document is selected. Go back and choose a state and county document first.");
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
        RadioGroup requirementGroup = findViewById(R.id.group_requirement);
        RadioButton selectedRequirement = findViewById(requirementGroup.getCheckedRadioButtonId());
        replacements.put("[[REQU]]", selectedRequirement == null
                ? "Not Required" : selectedRequirement.getText().toString());
        if (replacements.get("[[DEFENDANT]]").isEmpty()
                || replacements.get("[[CAUSENO]]").isEmpty()) {
            ((TextView) findViewById(R.id.txt_final_status))
                    .setText("Please enter Defendant and Cause No. Go back to Data Entry.");
            return;
        }
        pendingReplacements = replacements;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(DOCX_MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, "Generated_" + System.currentTimeMillis() + ".docx");
        ((TextView) findViewById(R.id.txt_final_status))
                .setText("Choose a folder and filename for the generated document. This is the save step, not document selection.");
        startActivityForResult(intent, CREATE_DOCX_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOCX_FILE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            saveGeneratedDocument(data.getData());
        } else if (requestCode == CREATE_DOCX_FILE) {
            pendingReplacements = null;
            TextView status = findViewById(R.id.txt_final_status);
            if (status != null) status.setText("Save cancelled.");
        }
    }

    private void saveGeneratedDocument(Uri outputUri) {
        if (pendingReplacements == null) return;
        try (InputStream input = getAssets().open(selectedTemplateAsset);
             OutputStream output = getContentResolver().openOutputStream(outputUri)) {
            if (input == null || output == null) throw new IllegalStateException("Could not open template or output.");
            DocxTemplateProcessor.generate(input, output, pendingReplacements);
            TextView status = findViewById(R.id.txt_final_status);
            if (status != null) status.setText("Document saved successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Document generation failed", t);
            TextView status = findViewById(R.id.txt_final_status);
            if (status != null) status.setText("Generation failed: " + t.getMessage() + "\n" + formatThrowable(t));
        } finally {
            pendingReplacements = null;
        }
    }
}
