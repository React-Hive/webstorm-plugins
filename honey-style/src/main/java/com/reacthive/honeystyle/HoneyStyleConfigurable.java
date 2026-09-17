package com.reacthive.honeystyle;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;

public final class HoneyStyleConfigurable implements Configurable {

    private final Project project;

    private JBCheckBox enabled;
    private JBCheckBox showInTemplates;
    private JBTextField discoveryFilter;
    private JBTextArea extraThemeFiles;
    private JBLabel status;
    private JPanel panel;

    public HoneyStyleConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Honey Style";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enabled = new JBCheckBox("Show theme color swatches");
        showInTemplates = new JBCheckBox("Also scan styled/css template literals for bare paths");
        discoveryFilter = new JBTextField();
        extraThemeFiles = new JBTextArea(4, 40);
        status = new JBLabel();

        JButton rescan = new JButton("Rescan theme files");
        rescan.addActionListener(e -> {
            HoneyThemeService.getInstance(project).reload();
            updateStatus();
        });

        JBScrollPane extraScroll = new JBScrollPane(extraThemeFiles);
        extraScroll.setPreferredSize(JBUI.size(480, 90));

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addComponent(showInTemplates)
                .addLabeledComponent("Scan .ts/.tsx files whose path contains:", discoveryFilter)
                .addLabeledComponent("Additional theme files (one project-relative path per line):", extraScroll)
                .addComponent(status)
                .addComponent(rescan)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        return panel;
    }

    @Override
    public boolean isModified() {
        HoneyStyleSettings settings = HoneyStyleSettings.getInstance(project);
        return enabled.isSelected() != settings.isEnabled()
                || showInTemplates.isSelected() != settings.isShowInTemplates()
                || !discoveryFilter.getText().trim().equals(settings.getDiscoveryFilter())
                || !readExtraFiles().equals(settings.getExtraThemeFiles());
    }

    @Override
    public void apply() {
        HoneyStyleSettings.getInstance(project).update(
                enabled.isSelected(),
                showInTemplates.isSelected(),
                discoveryFilter.getText().trim(),
                readExtraFiles());
        updateStatus();
    }

    @Override
    public void reset() {
        HoneyStyleSettings settings = HoneyStyleSettings.getInstance(project);
        enabled.setSelected(settings.isEnabled());
        showInTemplates.setSelected(settings.isShowInTemplates());
        discoveryFilter.setText(settings.getDiscoveryFilter());
        extraThemeFiles.setText(String.join("\n", settings.getExtraThemeFiles()));
        updateStatus();
    }

    private List<String> readExtraFiles() {
        List<String> files = new ArrayList<>();
        for (String line : extraThemeFiles.getText().split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                files.add(trimmed);
            }
        }
        return files;
    }

    private void updateStatus() {
        HoneyPalette palette;
        try {
            palette = HoneyThemeService.getInstance(project).getPalette();
        } catch (Exception e) {
            status.setText("Palette not available yet - reopen this page once indexing finishes.");
            return;
        }
        if (palette.isEmpty()) {
            status.setText("No theme colors found. Add an explicit theme file above, or widen the filter.");
            return;
        }
        status.setText("<html>" + palette.size() + " colors from: "
                + String.join(", ", palette.sourceFiles()) + "</html>");
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        enabled = null;
        showInTemplates = null;
        discoveryFilter = null;
        extraThemeFiles = null;
        status = null;
    }
}
