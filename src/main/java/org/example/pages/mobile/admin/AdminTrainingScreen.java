package org.example.pages.mobile.admin;

import io.appium.java_client.android.AndroidDriver;
import org.example.base.MobileBasePage;
import org.openqa.selenium.By;

import java.time.Duration;

/**
 * Training materials — {@code screens/Admin/training/admin_training_materials_page.dart}.
 *
 * <p>CRUD over the courses professionals see in their tutorials screen. The form has two validation
 * rules worth pinning: title and file URL are both required, and the video duration must be a whole
 * number of seconds ("Duration must be a whole number") — a fractional value is the easy mistake.
 */
public class AdminTrainingScreen extends MobileBasePage {

    // ---- copy used as assertions -------------------------------------------
    public static final String NEW = "New";
    public static final String TITLE_REQUIRED = "Title *";
    public static final String TYPE_REQUIRED = "Type *";
    public static final String FILE_URL_REQUIRED = "File URL *";
    public static final String DESCRIPTION = "Description";
    public static final String DURATION = "Duration (seconds, videos)";
    public static final String REQUIRED_ERROR = "Title and File URL are required";
    public static final String DURATION_ERROR = "Duration must be a whole number";
    public static final String DELETE = "Delete";
    public static final String DELETE_CONFIRM = "Delete material?";
    public static final String CANCEL = "Cancel";
    public static final String COMPLETED_BY = "Completed by";
    public static final String TYPE_VIDEO = "Video";
    public static final String TYPE_DOCUMENT = "Document";

    public AdminTrainingScreen(AndroidDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        return isPresent(accId(NEW), Duration.ofSeconds(25))
                || isPresentAfterScroll(COMPLETED_BY);
    }

    /** Opens the create-material dialog. */
    public AdminTrainingScreen createNew() {
        tap(accId(NEW));
        return this;
    }

    /** Fills the material form. Field order follows the dialog: title, description, file URL. */
    public AdminTrainingScreen fillMaterial(String title, String description, String fileUrl) {
        type(editText(0), title);
        type(editText(1), description);
        type(editText(2), fileUrl);
        hideKeyboard();
        return this;
    }

    /** Sets the duration field (videos only). */
    public AdminTrainingScreen setDuration(String seconds) {
        scrollToDesc("Duration");
        type(editText(3), seconds);
        hideKeyboard();
        return this;
    }

    /** Selects the material type. */
    public AdminTrainingScreen selectType(String type) {
        scrollAndTap("Type");
        scrollAndTap(type);
        return this;
    }

    /** Saves the material. */
    public AdminTrainingScreen save() {
        scrollAndTap("Save");
        return this;
    }

    public boolean showsRequiredFieldsError() {
        return isPresent(descContains(REQUIRED_ERROR), Duration.ofSeconds(10));
    }

    public boolean showsDurationError() {
        return isPresent(descContains(DURATION_ERROR), Duration.ofSeconds(10));
    }

    /** True if a material with this title is listed. */
    public boolean hasMaterial(String title) {
        return isPresentAfterScroll(title);
    }

    /** Deletes a material, confirming the prompt. */
    public AdminTrainingScreen deleteMaterial(String title) {
        scrollToDesc(title);
        tap(accId(DELETE));
        if (isPresent(descContains(DELETE_CONFIRM), SHORT_TIMEOUT)) {
            tap(accId(DELETE));
        }
        return this;
    }

    public boolean showsDeleteConfirmation() {
        return isPresent(descContains(DELETE_CONFIRM), Duration.ofSeconds(10));
    }

    /** True when a completion count is shown for a material. */
    public boolean showsCompletionCount() {
        return isPresentAfterScroll(COMPLETED_BY);
    }
}
