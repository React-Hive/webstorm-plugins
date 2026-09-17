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
    private JBTextArea customColors;
    private JBTextArea colorProps;
    private JBTextArea colorFunctions;
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
        customColors = new JBTextArea(5, 40);
        colorProps = new JBTextArea(5, 40);
        colorFunctions = new JBTextArea(3, 40);
        status = new JBLabel();

        JButton rescan = new JButton("Rescan theme files");
        rescan.addActionListener(e -> {
            HoneyThemeService.getInstance(project).reload();
            updateStatus();
        });

        JBScrollPane extraScroll = new JBScrollPane(extraThemeFiles);
        extraScroll.setPreferredSize(JBUI.size(480, 90));

        JBScrollPane colorsScroll = new JBScrollPane(customColors);
        colorsScroll.setPreferredSize(JBUI.size(480, 110));

        JBScrollPane propsScroll = new JBScrollPane(colorProps);
        propsScroll.setPreferredSize(JBUI.size(480, 110));

        JBScrollPane functionsScroll = new JBScrollPane(colorFunctions);
        functionsScroll.setPreferredSize(JBUI.size(480, 70));

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addComponent(showInTemplates)
                .addLabeledComponent("Scan .ts/.tsx files whose path contains:", discoveryFilter)
                .addLabeledComponent("Additional theme files (one project-relative path per line):", extraScroll)
                .addLabeledComponent("Extra color names (one \"name = value\" per line):", colorsScroll)
                .addComponentToRightColumn(new JBLabel(
                        "<html><small>The 148 CSS names are built in. Add your own, or redefine one,"
                                + " e.g. <code>brand = #318BFA</code>.</small></html>"))
                .addLabeledComponent("Color props (one per line):", propsScroll)
                .addComponentToRightColumn(new JBLabel(
                        "<html><small>JSX props that take a color path. The <code>$</code> is"
                                + " optional. Leave empty for honey-layout's defaults.</small></html>"))
                .addLabeledComponent("Color functions (one per line):", functionsScroll)
                .addComponentToRightColumn(new JBLabel(
                        "<html><small>Functions whose first string argument is a color path."
                                + " Defaults to honey-style's <code>resolveColor</code>; add your own"
                                + " wrappers here.</small></html>"))
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
                || !readExtraFiles().equals(settings.getExtraThemeFiles())
                || !readLines(customColors).equals(settings.getCustomColors())
                || !readLines(colorProps).equals(settings.getColorProps())
                || !readLines(colorFunctions).equals(settings.getColorFunctions());
    }

    @Override
    public void apply() {
        HoneyStyleSettings.getInstance(project).update(
                enabled.isSelected(),
                showInTemplates.isSelected(),
                discoveryFilter.getText().trim(),
                readExtraFiles(),
                readLines(customColors),
                readLines(colorProps),
                readLines(colorFunctions));
        updateStatus();
    }

    @Override
    public void reset() {
        HoneyStyleSettings settings = HoneyStyleSettings.getInstance(project);
        enabled.setSelected(settings.isEnabled());
        showInTemplates.setSelected(settings.isShowInTemplates());
        discoveryFilter.setText(settings.getDiscoveryFilter());
        extraThemeFiles.setText(String.join("\n", settings.getExtraThemeFiles()));
        customColors.setText(String.join("\n", settings.getCustomColors()));
        colorProps.setText(String.join("\n", settings.getColorProps()));
        colorFunctions.setText(String.join("\n", settings.getColorFunctions()));
        updateStatus();
    }

    private List<String> readExtraFiles() {
        return readLines(extraThemeFiles);
    }

    private static List<String> readLines(JBTextArea area) {
        List<String> lines = new ArrayList<>();
        for (String line : area.getText().split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
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
        customColors = null;
        colorProps = null;
        colorFunctions = null;
        status = null;
    }
}
