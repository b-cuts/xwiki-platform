package com.xpn.xwiki.plugin.applicationmanager.core.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Arrays;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.plugin.XWikiPluginInterface;

import com.xpn.xwiki.web.XWikiMessageTool;

/**
 * Plugin internationalization service based {@link XWikiMessageTool}.
 * 
 * @version $Id: $
 */
public class XWikiPluginMessageTool extends XWikiMessageTool
{
    /**
     * @param locale the locale.
     * @param plugin the plugin.
     * @param context the {@link com.xpn.xwiki.XWikiContext} object, used to get access to XWiki
     *            primitives for loading documents
     */
    public XWikiPluginMessageTool(Locale locale, XWikiPluginInterface plugin, XWikiContext context)
    {
        this(ResourceBundle.getBundle(plugin.getName() + "/ApplicationResources", locale == null
            ? Locale.ENGLISH : locale), context);
    }

    /**
     * @param bundle the default Resource Bundle to fall back to if no document bundle is found when
     *            trying to get a key
     */
    public XWikiPluginMessageTool(ResourceBundle bundle)
    {
        this(bundle, null);
    }

    /**
     * @param bundle the default Resource Bundle to fall back to if no document bundle is found when
     *            trying to get a key
     * @param context the {@link com.xpn.xwiki.XWikiContext} object, used to get access to XWiki
     *            primitives for loading documents
     */
    public XWikiPluginMessageTool(ResourceBundle bundle, XWikiContext context)
    {
        super(bundle, context);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Start calling <code>context</code>'s {@link XWikiMessageTool#get(String)} then if nothing
     * is found use plugin's {@link ResourceBundle}.
     * 
     * @see com.xpn.xwiki.web.XWikiMessageTool#getTranslation(java.lang.String)
     */
    protected String getTranslation(String key)
    {
        String translation = key;

        if (context != null) {
            translation = this.context.getMessageTool().get(key);
        }

        // Want to know if XWikiMessageTool.get return exactly the provided key string (means it
        // found nothing).
        if (translation == key) {
            try {
                translation = this.bundle.getString(key);
            } catch (Exception e) {
                translation = null;
            }
        }

        return translation;
    }

    /**
     * Find a translation and then replace any parameters found in the translation by the passed
     * params parameters. The format is the one used by {@link java.text.MessageFormat}.
     * 
     * @param key the key of the string to find
     * @param params the array of parameters to use for replacing "{N}" elements in the string. See
     *            {@link java.text.MessageFormat} for the full syntax
     * @return the translated string with parameters resolved
     * @see com.xpn.xwiki.web.XWikiMessageTool#get(String, List)
     */
    public String get(String key, String[] params)
    {
        return get(key, Arrays.asList(params));
    }

    /**
     * Find a translation and then replace any parameters found in the translation by the passed
     * param parameter. The format is the one used by {@link java.text.MessageFormat}.
     * 
     * @param key the key of the string to find
     * @param param the parameter to use for replacing "{0}" element in the string. See
     *            {@link java.text.MessageFormat} for the full syntax
     * @return the translated string with parameters resolved
     * @see com.xpn.xwiki.web.XWikiMessageTool#get(String, List)
     */
    public String get(String key, String param)
    {
        List paramList = new ArrayList(1);

        paramList.add(param);

        return get(key, paramList);
    }
}
