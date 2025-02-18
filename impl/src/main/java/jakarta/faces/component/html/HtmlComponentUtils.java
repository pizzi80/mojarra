package jakarta.faces.component.html;

import static com.sun.faces.renderkit.RenderKitUtils.ATTRIBUTES_THAT_ARE_SET_KEY;
import static com.sun.faces.renderkit.RenderKitUtils.OPTIMIZED_PACKAGE;

import java.util.ArrayList;
import java.util.List;

import jakarta.el.ValueExpression;
import jakarta.faces.component.UIComponent;

public final class HtmlComponentUtils {
    private HtmlComponentUtils() {}


    public static void handleAttribute(UIComponent component, String name, Object value) {

        @SuppressWarnings("unchecked")
        final List<String> setAttributes = (List<String>) component.getAttributes().computeIfAbsent(ATTRIBUTES_THAT_ARE_SET_KEY,
                $ -> component.getClass().getName().startsWith(OPTIMIZED_PACKAGE) ? new ArrayList<>(6) : null);

        if (setAttributes != null) {
            if (value == null) {
                ValueExpression ve = component.getValueExpression(name);
                if (ve == null) {
                    setAttributes.remove(name);
                }
            } else if (!setAttributes.contains(name)) {
                setAttributes.add(name);
            }
        }
    }

}
