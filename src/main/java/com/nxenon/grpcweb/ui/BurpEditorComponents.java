package com.nxenon.grpcweb.ui;

import burp.api.montoya.ui.editor.Editor;

import java.awt.Component;
import java.awt.Container;
import javax.swing.text.JTextComponent;

/**
 * Locates the Swing text component inside a Burp {@link Editor}.
 *
 * <p>Burp's {@code createRawEditor()} returns a composite whose editable surface is a nested
 * {@code JTextArea} named {@code syntaxTextArea}. Right-click handlers must be attached to that
 * child; listening only on {@link Editor#uiComponent()} misses the clicks.
 */
final class BurpEditorComponents {

    private static final String SYNTAX_TEXT_AREA = "syntaxTextArea";

    private BurpEditorComponents() {
    }

    /**
     * The component that should receive mouse listeners for a right-click menu.
     *
     * <p>Prefers Burp's named text area, then any nested {@link JTextComponent}, and finally the
     * editor root so installation still succeeds against test doubles that return a plain panel.
     */
    static Component editableSurface(Editor editor) {
        Component root = editor.uiComponent();
        if (root == null) {
            return null;
        }
        Component named = findByName(root, SYNTAX_TEXT_AREA);
        if (named != null) {
            return named;
        }
        Component text = findTextComponent(root);
        return text != null ? text : root;
    }

    private static Component findByName(Component component, String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = findByName(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Component findTextComponent(Component component) {
        // JTextArea is a JTextComponent; one check covers Burp's syntax area and plain editors.
        if (component instanceof JTextComponent) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component match = findTextComponent(child);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
