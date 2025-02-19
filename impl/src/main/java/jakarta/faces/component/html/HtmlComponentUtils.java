package jakarta.faces.component.html;

import static com.sun.faces.renderkit.RenderKitUtils.ATTRIBUTES_THAT_ARE_SET_KEY;
import static com.sun.faces.renderkit.RenderKitUtils.OPTIMIZED_PACKAGE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.el.ValueExpression;
import jakarta.faces.component.UIComponent;

public final class HtmlComponentUtils {
    private HtmlComponentUtils() {}


    public static void handleAttribute(UIComponent component, String name, Object value) {
        final Map<String, Object> attributes = component.getAttributes();

        @SuppressWarnings("unchecked")
        List<String> setAttributes = (List<String>) attributes.get(ATTRIBUTES_THAT_ARE_SET_KEY);

        if (setAttributes == null) {
            String className = component.getClass().getName();
            if (className.startsWith(OPTIMIZED_PACKAGE)) {
                setAttributes = new ArrayList<>(6);
                attributes.put(ATTRIBUTES_THAT_ARE_SET_KEY, setAttributes);
            }
        }

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
