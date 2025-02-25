package com.sun.faces.config.configpopulator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import jakarta.faces.application.ApplicationConfigurationPopulator;

public final class MojarraRuntimePopulator extends ApplicationConfigurationPopulator {

    @Override
    public void populateApplicationConfiguration(Document document) {
        final String ns = document.getDocumentElement().getNamespaceURI();
        final Element facesConfigElement = document.getDocumentElement();

        // Factory ------------------------------------------------------------------------------------------------------------------
        populateFactories(document, ns, facesConfigElement);

        // Application -----------------------------------------------------------------------------------------
        populateApplication(document, ns, facesConfigElement);

        // Converters byId -----------------------------------------------------------------------------------------
        populateConvertersById(document, ns, facesConfigElement);

        // Converter forClass --------------------------------------------------------------------------------------------
        populateConvertersByClass(document, ns, facesConfigElement);

        // LifeCycle -------------------------------------------------------------------------------------------------------------------
        final Element lifecycleElement = document.createElementNS(ns, "lifecycle");
        createElement(document, ns, "phase-listener", "com.sun.faces.lifecycle.ELResolverInitPhaseListener", lifecycleElement);
        facesConfigElement.appendChild(lifecycleElement);

        // Behavior -------------------------------------------------------------------------------------------------------------------
        final Element behaviorElement = document.createElementNS(ns, "behavior");
        createElement(document, ns, "behavior-id", "jakarta.faces.behavior.Ajax", behaviorElement);
        createElement(document, ns, "behavior-class", "jakarta.faces.component.behavior.AjaxBehavior", behaviorElement);
        facesConfigElement.appendChild(behaviorElement);

        // Validator byId --------------------------------------------------------------------------------------------------------------
        populateValidators(document, ns, facesConfigElement);

        // RenderKit  --------------------------------------------------------------------------------------------------------------
        final Element renderKitElement = document.createElementNS(ns, "render-kit");
        createElement(document, ns, "render-kit-id", "HTML_BASIC", renderKitElement);
        {
            Element rendererElement = document.createElementNS(ns, "renderer");
            createElement(document, ns, "component-family", "facelets", rendererElement);
            createElement(document, ns, "renderer-type", "facelets.ui.Repeat", rendererElement);
            createElement(document, ns, "renderer-class", "com.sun.faces.facelets.component.RepeatRenderer", rendererElement);
            renderKitElement.appendChild(rendererElement);
        }
        {
            Element client_behavior_rendererElement = document.createElementNS(ns, "client-behavior-renderer");
            createElement(document, ns, "client-behavior-renderer-type", "jakarta.faces.behavior.Ajax", client_behavior_rendererElement);
            createElement(document, ns, "client-behavior-renderer-class", "com.sun.faces.renderkit.html_basic.AjaxBehaviorRenderer", client_behavior_rendererElement);
            renderKitElement.appendChild(client_behavior_rendererElement);
            facesConfigElement.appendChild(renderKitElement);
        }

        // Components  --------------------------------------------------------------------------------------------------------------
        populateComponents(document, ns, facesConfigElement);

        // Renderers ------------------------------------------------------------------------------------------------------------------
        populateRenderers(document, ns, facesConfigElement);
    }

    // Sections ------------------------------------------------------------------------------------------------------------

    private void populateFactories(Document document, String ns, Element facesConfigElement) {
        final Element factoryElement = document.createElementNS(ns, "factory");
        final String[][] FACTORY_ELEMENTS = {
                {"application-factory", "com.sun.faces.application.ApplicationFactoryImpl"},
                {"exception-handler-factory", "com.sun.faces.context.ExceptionHandlerFactoryImpl"},
                {"visit-context-factory", "com.sun.faces.component.visit.VisitContextFactoryImpl"},
                {"faces-context-factory", "com.sun.faces.context.FacesContextFactoryImpl"},
                {"client-window-factory", "com.sun.faces.lifecycle.ClientWindowFactoryImpl"},
                {"flash-factory", "com.sun.faces.context.flash.FlashFactoryImpl"},
                {"partial-view-context-factory", "com.sun.faces.context.PartialViewContextFactoryImpl"},
                {"lifecycle-factory", "com.sun.faces.lifecycle.LifecycleFactoryImpl"},
                {"render-kit-factory", "com.sun.faces.renderkit.RenderKitFactoryImpl"},
                {"view-declaration-language-factory", "com.sun.faces.application.view.ViewDeclarationLanguageFactoryImpl"},
                {"tag-handler-delegate-factory", "com.sun.faces.facelets.tag.faces.TagHandlerDelegateFactoryImpl"},
                {"external-context-factory", "com.sun.faces.context.ExternalContextFactoryImpl"},
                {"facelet-cache-factory", "com.sun.faces.facelets.impl.FaceletCacheFactoryImpl"},
                {"flow-handler-factory", "com.sun.faces.flow.FlowHandlerFactoryImpl"},
                {"search-expression-context-factory", "com.sun.faces.component.search.SearchExpressionContextFactoryImpl"}
        };
        for (String[] factory : FACTORY_ELEMENTS) createElement(document, ns, factory[0], factory[1], factoryElement);
        facesConfigElement.appendChild(factoryElement);
    }

    private void populateApplication(Document document, String ns, Element facesConfigElement) {
        final Element applicationElement = document.createElementNS(ns, "application");
        createElement(document, ns, "action-listener", "com.sun.faces.application.ActionListenerImpl", applicationElement);
        createElement(document, ns, "navigation-handler", "com.sun.faces.application.NavigationHandlerImpl", applicationElement);
        createElement(document, ns, "state-manager", "com.sun.faces.application.StateManagerImpl", applicationElement);
        createElement(document, ns, "view-handler", "com.sun.faces.application.view.MultiViewHandler", applicationElement);
        createElement(document, ns, "resource-handler", "com.sun.faces.application.resource.ResourceHandlerImpl", applicationElement);
        createElement(document, ns, "search-expression-handler", "com.sun.faces.component.search.SearchExpressionHandlerImpl", applicationElement);
        // Application > SystemEventListener -----------------------------------------------------------------------
        {
            final Element systemEventListenerElement = document.createElementNS(ns, "system-event-listener");
            createElement(document, ns, "system-event-listener-class", "com.sun.faces.application.view.ViewScopeEventListener", systemEventListenerElement);
            createElement(document, ns, "system-event-class", "jakarta.faces.event.PostConstructViewMapEvent", systemEventListenerElement);
            createElement(document, ns, "source-class", "jakarta.faces.component.UIViewRoot", systemEventListenerElement);
            applicationElement.appendChild(systemEventListenerElement);
        }
        {
            final Element systemEventListenerElement = document.createElementNS(ns, "system-event-listener");
            createElement(document, ns, "system-event-listener-class", "com.sun.faces.application.view.ViewScopeEventListener", systemEventListenerElement);
            createElement(document, ns, "system-event-class", "jakarta.faces.event.PreDestroyViewMapEvent", systemEventListenerElement);
            createElement(document, ns, "source-class", "jakarta.faces.component.UIViewRoot", systemEventListenerElement);
            applicationElement.appendChild(systemEventListenerElement);
        }
        facesConfigElement.appendChild(applicationElement);
    }

    private void populateConvertersById(Document document, String ns, Element facesConfigElement) {
        final String[][] CONVERTERS_BY_ID = {
                {"jakarta.faces.BigDecimal", "jakarta.faces.convert.BigDecimalConverter"},
                {"jakarta.faces.BigInteger", "jakarta.faces.convert.BigIntegerConverter"},
                {"jakarta.faces.Boolean", "jakarta.faces.convert.BooleanConverter"},
                {"jakarta.faces.Byte", "jakarta.faces.convert.ByteConverter"},
                {"jakarta.faces.Character", "jakarta.faces.convert.CharacterConverter"},
                {"jakarta.faces.DateTime", "jakarta.faces.convert.DateTimeConverter"},
                {"jakarta.faces.Double", "jakarta.faces.convert.DoubleConverter"},
                {"jakarta.faces.Float", "jakarta.faces.convert.FloatConverter"},
                {"jakarta.faces.Integer", "jakarta.faces.convert.IntegerConverter"},
                {"jakarta.faces.Long", "jakarta.faces.convert.LongConverter"},
                {"jakarta.faces.Number", "jakarta.faces.convert.NumberConverter"},
                {"jakarta.faces.Short", "jakarta.faces.convert.ShortConverter"},
                {"jakarta.faces.UUID", "jakarta.faces.convert.UUIDConverter"}
        };
        for (String[] converter : CONVERTERS_BY_ID) {
            createAndAddConverterByIdElement(document, ns, facesConfigElement, converter[0], converter[1]);
        }
    }

    private void populateConvertersByClass(Document document, String ns, Element facesConfigElement) {
        final String[][] CONVERTERS_FOR_CLASS = {
                {"java.math.BigDecimal", "jakarta.faces.convert.BigDecimalConverter"},
                {"java.math.BigInteger", "jakarta.faces.convert.BigIntegerConverter"},
                {"java.lang.Boolean", "jakarta.faces.convert.BooleanConverter"},
                {"java.lang.Byte", "jakarta.faces.convert.ByteConverter"},
                {"java.lang.Character", "jakarta.faces.convert.CharacterConverter"},
                {"java.lang.Double", "jakarta.faces.convert.DoubleConverter"},
                {"java.lang.Float", "jakarta.faces.convert.FloatConverter"},
                {"java.lang.Integer", "jakarta.faces.convert.IntegerConverter"},
                {"java.lang.Long", "jakarta.faces.convert.LongConverter"},
                {"java.lang.Short", "jakarta.faces.convert.ShortConverter"},
                {"java.lang.Enum", "jakarta.faces.convert.EnumConverter"},
                {"java.util.UUID", "jakarta.faces.convert.UUIDConverter"}
        };
        for (String[] converter : CONVERTERS_FOR_CLASS) {
            createConverterForClassElement(document, ns, facesConfigElement, converter[0], converter[1]);
        }
    }

    private void populateValidators(Document document, String ns, Element facesConfigElement) {
        final String[][] VALIDATORS = {
                {"jakarta.faces.Bean", "jakarta.faces.validator.BeanValidator"},
                {"jakarta.faces.DoubleRange", "jakarta.faces.validator.DoubleRangeValidator"},
                {"jakarta.faces.Length", "jakarta.faces.validator.LengthValidator"},
                {"jakarta.faces.LongRange", "jakarta.faces.validator.LongRangeValidator"},
                {"jakarta.faces.RegularExpression", "jakarta.faces.validator.RegexValidator"},
                {"jakarta.faces.Required", "jakarta.faces.validator.RequiredValidator"}
        };
        for (String[] validator : VALIDATORS) {
            Element validatorElement = document.createElementNS(ns, "validator");
            createElement(document, ns, "validator-id", validator[0], validatorElement);
            createElement(document, ns, "validator-class", validator[1], validatorElement);
            facesConfigElement.appendChild(validatorElement);
        }
    }

    private void populateComponents(Document document, String ns, Element facesConfigElement) {
        final String[][] COMPONENTS = {
                {"com.sun.faces.ext.validateWholeBean", "com.sun.faces.ext.component.UIValidateWholeBean"},
                {"facelets.ui.Repeat", "com.sun.faces.facelets.component.UIRepeat"},
                {"facelets.ui.ComponentRef", "com.sun.faces.facelets.tag.ui.ComponentRef"},
                {"facelets.ui.Debug", "com.sun.faces.facelets.tag.ui.UIDebug"},
                {"jakarta.faces.Composite", "com.sun.faces.facelets.tag.faces.CompositeComponentImpl"},
                {"jakarta.faces.ComponentResourceContainer", "com.sun.faces.component.ComponentResourceContainer"},
                {"jakarta.faces.Column", "jakarta.faces.component.UIColumn"},
                {"jakarta.faces.Command", "jakarta.faces.component.UICommand"},
                {"jakarta.faces.Data", "jakarta.faces.component.UIData"},
                {"jakarta.faces.Form", "jakarta.faces.component.UIForm"},
                {"jakarta.faces.Graphic", "jakarta.faces.component.UIGraphic"},
                {"jakarta.faces.ImportConstants", "jakarta.faces.component.UIImportConstants"},
                {"jakarta.faces.Input", "jakarta.faces.component.UIInput"},
                {"jakarta.faces.Message", "jakarta.faces.component.UIMessage"},
                {"jakarta.faces.Messages", "jakarta.faces.component.UIMessages"},
                {"jakarta.faces.NamingContainer", "jakarta.faces.component.UINamingContainer"},
                {"jakarta.faces.Output", "jakarta.faces.component.UIOutput"},
                {"jakarta.faces.OutcomeTarget", "jakarta.faces.component.UIOutcomeTarget"},
                {"jakarta.faces.Panel", "jakarta.faces.component.UIPanel"},
                {"jakarta.faces.ViewParameter", "jakarta.faces.component.UIViewParameter"},
                {"jakarta.faces.ViewAction", "jakarta.faces.component.UIViewAction"},
                {"jakarta.faces.Parameter", "jakarta.faces.component.UIParameter"},
                {"jakarta.faces.SelectBoolean", "jakarta.faces.component.UISelectBoolean"},
                {"jakarta.faces.SelectItem", "jakarta.faces.component.UISelectItem"},
                {"jakarta.faces.SelectItems", "jakarta.faces.component.UISelectItems"},
                {"jakarta.faces.SelectItemGroup", "jakarta.faces.component.UISelectItemGroup"},
                {"jakarta.faces.SelectItemGroups", "jakarta.faces.component.UISelectItemGroups"},
                {"jakarta.faces.SelectMany", "jakarta.faces.component.UISelectMany"},
                {"jakarta.faces.SelectOne", "jakarta.faces.component.UISelectOne"},
                {"jakarta.faces.ViewRoot", "jakarta.faces.component.UIViewRoot"},
                {"jakarta.faces.Websocket", "jakarta.faces.component.UIWebsocket"},
                {"jakarta.faces.HtmlColumn", "jakarta.faces.component.html.HtmlColumn"},
                {"jakarta.faces.HtmlCommandButton", "jakarta.faces.component.html.HtmlCommandButton"},
                {"jakarta.faces.HtmlCommandLink", "jakarta.faces.component.html.HtmlCommandLink"},
                {"jakarta.faces.HtmlCommandScript", "jakarta.faces.component.html.HtmlCommandScript"},
                {"jakarta.faces.HtmlDataTable", "jakarta.faces.component.html.HtmlDataTable"},
                {"jakarta.faces.HtmlForm", "jakarta.faces.component.html.HtmlForm"},
                {"jakarta.faces.HtmlGraphicImage", "jakarta.faces.component.html.HtmlGraphicImage"},
                {"jakarta.faces.HtmlInputFile", "jakarta.faces.component.html.HtmlInputFile"},
                {"jakarta.faces.HtmlInputHidden", "jakarta.faces.component.html.HtmlInputHidden"},
                {"jakarta.faces.HtmlInputSecret", "jakarta.faces.component.html.HtmlInputSecret"},
                {"jakarta.faces.HtmlInputText", "jakarta.faces.component.html.HtmlInputText"},
                {"jakarta.faces.HtmlInputTextarea", "jakarta.faces.component.html.HtmlInputTextarea"},
                {"jakarta.faces.HtmlMessage", "jakarta.faces.component.html.HtmlMessage"},
                {"jakarta.faces.HtmlMessages", "jakarta.faces.component.html.HtmlMessages"},
                {"jakarta.faces.HtmlOutputFormat", "jakarta.faces.component.html.HtmlOutputFormat"},
                {"jakarta.faces.HtmlOutputLabel", "jakarta.faces.component.html.HtmlOutputLabel"},
                {"jakarta.faces.HtmlOutputLink", "jakarta.faces.component.html.HtmlOutputLink"},
                {"jakarta.faces.HtmlOutcomeTargetLink", "jakarta.faces.component.html.HtmlOutcomeTargetLink"},
                {"jakarta.faces.HtmlOutcomeTargetButton", "jakarta.faces.component.html.HtmlOutcomeTargetButton"},
                {"jakarta.faces.HtmlOutputText", "jakarta.faces.component.html.HtmlOutputText"},
                {"jakarta.faces.HtmlPanelGrid", "jakarta.faces.component.html.HtmlPanelGrid"},
                {"jakarta.faces.HtmlPanelGroup", "jakarta.faces.component.html.HtmlPanelGroup"},
                {"jakarta.faces.HtmlSelectBooleanCheckbox", "jakarta.faces.component.html.HtmlSelectBooleanCheckbox"},
                {"jakarta.faces.HtmlSelectManyCheckbox", "jakarta.faces.component.html.HtmlSelectManyCheckbox"},
                {"jakarta.faces.HtmlSelectManyListbox", "jakarta.faces.component.html.HtmlSelectManyListbox"},
                {"jakarta.faces.HtmlSelectManyMenu", "jakarta.faces.component.html.HtmlSelectManyMenu"},
                {"jakarta.faces.HtmlSelectOneListbox", "jakarta.faces.component.html.HtmlSelectOneListbox"},
                {"jakarta.faces.HtmlSelectOneMenu", "jakarta.faces.component.html.HtmlSelectOneMenu"},
                {"jakarta.faces.HtmlSelectOneRadio", "jakarta.faces.component.html.HtmlSelectOneRadio"},
                {"jakarta.faces.OutputDoctype", "jakarta.faces.component.html.HtmlDoctype"},
                {"jakarta.faces.OutputHead", "jakarta.faces.component.html.HtmlHead"},
                {"jakarta.faces.OutputBody", "jakarta.faces.component.html.HtmlBody"}
        };
        for (String[] component: COMPONENTS) {
            Element componentElement = document.createElementNS(ns, "component");
            createElement(document, ns, "component-type", component[0], componentElement);
            createElement(document, ns, "component-class", component[1], componentElement);
            facesConfigElement.appendChild(componentElement);
        }
    }

    private void populateRenderers(Document document, String ns, Element facesConfigElement) {
        final String[][] RENDERERS = {
                {"jakarta.faces.Command", "jakarta.faces.Button", "com.sun.faces.renderkit.html_basic.ButtonRenderer"},
                {"jakarta.faces.Command", "jakarta.faces.Link", "com.sun.faces.renderkit.html_basic.CommandLinkRenderer"},
                {"jakarta.faces.Command", "jakarta.faces.Script", "com.sun.faces.renderkit.html_basic.CommandScriptRenderer"},
                {"jakarta.faces.Data", "jakarta.faces.Table", "com.sun.faces.renderkit.html_basic.TableRenderer"},
                {"jakarta.faces.Form", "jakarta.faces.Form", "com.sun.faces.renderkit.html_basic.FormRenderer"},
                {"jakarta.faces.Graphic", "jakarta.faces.Image", "com.sun.faces.renderkit.html_basic.ImageRenderer"},
                {"jakarta.faces.Panel", "jakarta.faces.passthrough.Element", "com.sun.faces.renderkit.html_basic.PassthroughRenderer"},
                {"jakarta.faces.Input", "jakarta.faces.File", "com.sun.faces.renderkit.html_basic.FileRenderer"},
                {"jakarta.faces.Input", "jakarta.faces.Hidden", "com.sun.faces.renderkit.html_basic.HiddenRenderer"},
                {"jakarta.faces.Input", "jakarta.faces.Secret", "com.sun.faces.renderkit.html_basic.SecretRenderer"},
                {"jakarta.faces.Input", "jakarta.faces.Text", "com.sun.faces.renderkit.html_basic.TextRenderer"},
                {"jakarta.faces.Input", "jakarta.faces.Textarea", "com.sun.faces.renderkit.html_basic.TextareaRenderer"},
                {"jakarta.faces.Message", "jakarta.faces.Message", "com.sun.faces.renderkit.html_basic.MessageRenderer"},
                {"jakarta.faces.Messages", "jakarta.faces.Messages", "com.sun.faces.renderkit.html_basic.MessagesRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Format", "com.sun.faces.renderkit.html_basic.OutputMessageRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Label", "com.sun.faces.renderkit.html_basic.LabelRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Link", "com.sun.faces.renderkit.html_basic.OutputLinkRenderer"},
                {"jakarta.faces.OutcomeTarget", "jakarta.faces.Link", "com.sun.faces.renderkit.html_basic.OutcomeTargetLinkRenderer"},
                {"jakarta.faces.OutcomeTarget", "jakarta.faces.Button", "com.sun.faces.renderkit.html_basic.OutcomeTargetButtonRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Text", "com.sun.faces.renderkit.html_basic.TextRenderer"},
                {"jakarta.faces.Panel", "jakarta.faces.Grid", "com.sun.faces.renderkit.html_basic.GridRenderer"},
                {"jakarta.faces.Panel", "jakarta.faces.Group", "com.sun.faces.renderkit.html_basic.GroupRenderer"},
                {"jakarta.faces.SelectBoolean", "jakarta.faces.Checkbox", "com.sun.faces.renderkit.html_basic.CheckboxRenderer"},
                {"jakarta.faces.SelectMany", "jakarta.faces.Checkbox", "com.sun.faces.renderkit.html_basic.SelectManyCheckboxListRenderer"},
                {"jakarta.faces.SelectMany", "jakarta.faces.Listbox", "com.sun.faces.renderkit.html_basic.ListboxRenderer"},
                {"jakarta.faces.SelectMany", "jakarta.faces.Menu", "com.sun.faces.renderkit.html_basic.MenuRenderer"},
                {"jakarta.faces.SelectOne", "jakarta.faces.Listbox", "com.sun.faces.renderkit.html_basic.ListboxRenderer"},
                {"jakarta.faces.SelectOne", "jakarta.faces.Menu", "com.sun.faces.renderkit.html_basic.MenuRenderer"},
                {"jakarta.faces.SelectOne", "jakarta.faces.Radio", "com.sun.faces.renderkit.html_basic.RadioRenderer"},
                {"jakarta.faces.NamingContainer", "jakarta.faces.Composite", "com.sun.faces.renderkit.html_basic.CompositeRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.CompositeFacet", "com.sun.faces.renderkit.html_basic.CompositeFacetRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.resource.Script", "com.sun.faces.renderkit.html_basic.ScriptRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.resource.Stylesheet", "com.sun.faces.renderkit.html_basic.StylesheetRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Doctype", "com.sun.faces.renderkit.html_basic.DoctypeRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Head", "com.sun.faces.renderkit.html_basic.HeadRenderer"},
                {"jakarta.faces.Output", "jakarta.faces.Body", "com.sun.faces.renderkit.html_basic.BodyRenderer"},
                {"jakarta.faces.Script", "jakarta.faces.Websocket", "com.sun.faces.renderkit.html_basic.WebsocketRenderer"}
        };
        final Element renderKitElement = document.createElementNS(ns, "render-kit");
        for (String[] renderer : RENDERERS) {
            Element rendererElement = document.createElementNS(ns, "renderer");
            createElement(document, ns, "component-family", renderer[0], rendererElement);
            createElement(document, ns, "renderer-type", renderer[1], rendererElement);
            createElement(document, ns, "renderer-class", renderer[2], rendererElement);
            renderKitElement.appendChild(rendererElement);
        }
        facesConfigElement.appendChild(renderKitElement);
    }


    // Utils -------------------------------------------------------------------------------------------------------------------------------------------

    private static void createAndAddConverterByIdElement(Document document, String ns, Element parent, String converterId, String converterClass) {
        Element converterElement = document.createElementNS(ns, "converter");
        createElement(document, ns, "converter-id", converterId, converterElement);
        createElement(document, ns, "converter-class", converterClass, converterElement);
        parent.appendChild(converterElement);
    }

    private static void createConverterForClassElement(Document document, String ns, Element parent, String className, String converterClass) {
        Element converterElement = document.createElementNS(ns, "converter");
        createElement(document, ns, "converter-for-class", className, converterElement);
        createElement(document, ns, "converter-class", converterClass, converterElement);
        parent.appendChild(converterElement);
    }

    private static void createElement(Document document, String ns, String tag, String text, Element parent) {
        Element element = document.createElementNS(ns, tag);
        element.appendChild(document.createTextNode(text));
        parent.appendChild(element);
    }

}
