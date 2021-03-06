/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.export.html;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.context.ExecutionContextException;
import org.xwiki.context.ExecutionContextManager;
import org.xwiki.environment.Environment;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.url.URLContextManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.util.Util;
import com.xpn.xwiki.web.ExportURLFactory;
import com.xpn.xwiki.web.Utils;

/**
 * Create a ZIP package containing a range of HTML pages with skin and attachment dependencies.
 *
 * @version $Id$
 * @since XWiki Platform 1.3M1
 */
public class HtmlPackager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlPackager.class);

    /**
     * A point.
     */
    private static final String POINT = ".";

    /**
     * Name of the context property containing the document.
     */
    private static final String CONTEXT_TDOC = "tdoc";

    /**
     * Name of the Velocity context property containing the document.
     */
    private static final String VCONTEXT_DOC = "doc";

    /**
     * Name of the Velocity context property containing the document.
     */
    private static final String VCONTEXT_CDOC = "cdoc";

    /**
     * Name of the Velocity context property containing the document.
     */
    private static final String VCONTEXT_TDOC = CONTEXT_TDOC;

    /**
     * The separator in an internal zip path.
     */
    private static final String ZIPPATH_SEPARATOR = "/";

    /**
     * The name of the package for which packager append ".zip".
     */
    private String name = "html.export";

    /**
     * A description of the package.
     */
    private String description = "";

    /**
     * The pages to export. A {@link Set} of page name.
     */
    private Set<String> pages = new HashSet<String>();

    /**
     * Used to get the temporary directory.
     */
    private Environment environment = Utils.getComponent((Type) Environment.class);

    /**
     * Modify the name of the package for which packager append ".zip".
     *
     * @param name the name of the page.
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the name of the package for which packager append ".zip".
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Modify the description of the package.
     *
     * @param description the description of the package.
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return the description of the package.
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * Add a page to export.
     *
     * @param page the name of the page to export.
     */
    public void addPage(String page)
    {
        this.pages.add(page);
    }

    /**
     * Add a range of pages to export.
     *
     * @param pages a range of pages to export.
     */
    public void addPages(Collection<String> pages)
    {
        this.pages.addAll(pages);
    }

    /**
     * Add rendered document to ZIP stream.
     *
     * @param pageName the name (used with {@link com.xpn.xwiki.XWiki#getDocument(String, XWikiContext)}) of the page to
     *            render.
     * @param zos the ZIP output stream.
     * @param context the XWiki context.
     * @throws XWikiException error when rendering document.
     * @throws IOException error when rendering document.
     */
    private void renderDocument(String pageName, ZipOutputStream zos, XWikiContext context)
        throws XWikiException, IOException
    {
        DocumentReferenceResolver<String> resolver =
            Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, "current");
        DocumentReference docReference = resolver.resolve(pageName);
        XWikiDocument doc = context.getWiki().getDocument(docReference, context);

        if (doc.isNew()) {
            // Skip non-existing documents.
            return;
        }

        String zipname = doc.getDocumentReference().getWikiReference().getName();
        for (EntityReference space : doc.getDocumentReference().getSpaceReferences()) {
            zipname += POINT + space.getName();
        }
        zipname += POINT + doc.getDocumentReference().getName();
        String language = doc.getLanguage();
        if (language != null && language.length() != 0) {
            zipname += POINT + language;
        }

        zipname += ".html";

        ZipEntry zipentry = new ZipEntry(zipname);
        zos.putNextEntry(zipentry);

        String originalDatabase = context.getWikiId();
        try {
            context.setWikiId(doc.getDocumentReference().getWikiReference().getName());
            context.setDoc(doc);

            XWikiDocument tdoc = doc.getTranslatedDocument(context);
            context.put(CONTEXT_TDOC, tdoc);

            String content = evaluateDocumentContent(context);

            zos.write(content.getBytes(context.getWiki().getEncoding()));
            zos.closeEntry();
        } finally {
            context.setWikiId(originalDatabase);
        }
    }

    private String evaluateDocumentContent(XWikiContext context) throws IOException
    {
        context.getWiki().getPluginManager().beginParsing(context);
        Utils.enablePlaceholders(context);
        String content;
        try {
            content = context.getWiki().evaluateTemplate("view.vm", context);
            content = Utils.replacePlaceholders(content, context);
        } finally {
            Utils.disablePlaceholders(context);
        }
        content = context.getWiki().getPluginManager().endParsing(content.trim(), context);
        return content;
    }

    /**
     * Init provided {@link ExportURLFactory} and add rendered documents to ZIP stream.
     *
     * @param zos the ZIP output stream.
     * @param tempdir the directory where to copy attached files.
     * @param urlf the {@link com.xpn.xwiki.web.XWikiURLFactory} used to render the documents.
     * @param context the XWiki context.
     * @throws XWikiException error when render documents.
     * @throws IOException error when render documents.
     */
    private void renderDocuments(ZipOutputStream zos, File tempdir, ExportURLFactory urlf, XWikiContext context)
        throws XWikiException, IOException
    {
        ExecutionContextManager ecim = Utils.getComponent(ExecutionContextManager.class);
        Execution execution = Utils.getComponent(Execution.class);

        try {
            XWikiContext renderContext = context.clone();
            renderContext.put("action", "view");

            ExecutionContext executionContext = ecim.clone(execution.getContext());

            // Bridge with old XWiki Context, required for legacy code.
            executionContext.setProperty("xwikicontext", renderContext);

            // Push a clean new Execution Context since we don't want the main Execution Context to be used for
            // rendering the HTML pages to export. It's cleaner to isolate it as we do. Note that the new
            // Execution Context automatically gets initialized with a new Velocity Context by
            // the VelocityRequestInitializer class.
            execution.pushContext(executionContext);

            try {
                // Set the URL Factories/Serializer to use
                urlf.init(this.pages, tempdir, renderContext);
                renderContext.setURLFactory(urlf);
                Utils.getComponent(URLContextManager.class).setURLFormatId("filesystem");

                for (String pageName : this.pages) {
                    renderDocument(pageName, zos, renderContext);
                }
            } finally {
                execution.popContext();
            }
        } catch (ExecutionContextException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_EXPORT, XWikiException.ERROR_XWIKI_INIT_FAILED,
                "Failed to initialize Execution Context", e);
        }
    }

    /**
     * Apply export and create the ZIP package.
     *
     * @param context the XWiki context used to render pages.
     * @throws IOException error when creating the package.
     * @throws XWikiException error when render the pages.
     */
    public void export(XWikiContext context) throws IOException, XWikiException
    {
        context.getResponse().setContentType("application/zip");
        context.getResponse().addHeader("Content-disposition",
            "attachment; filename=" + Util.encodeURI(this.name, context) + ".zip");
        context.setFinished(true);

        ZipOutputStream zos = new ZipOutputStream(context.getResponse().getOutputStream());

        File dir = this.environment.getTemporaryDirectory();
        File tempdir = new File(dir, RandomStringUtils.randomAlphanumeric(8));
        tempdir.mkdirs();
        File attachmentDir = new File(tempdir, "attachment");
        attachmentDir.mkdirs();

        // Create custom URL factory
        ExportURLFactory urlf = new ExportURLFactory();

        // Render pages to export
        renderDocuments(zos, tempdir, urlf, context);

        // Add required skins to ZIP file
        for (String skinName : urlf.getFilesystemExportContext().getNeededSkins()) {
            addSkinToZip(skinName, zos, urlf.getFilesystemExportContext().getExportedSkinFiles(), context);
        }

        // Copy generated files in the ZIP file.
        addDirToZip(tempdir, TrueFileFilter.TRUE, zos, "", null);

        zos.setComment(this.description);

        // Finish ZIP file
        zos.finish();
        zos.flush();

        // Delete temporary directory
        deleteDirectory(tempdir);
    }

    /**
     * Delete a directory and all with all it's content.
     *
     * @param directory the directory to delete.
     */
    private static void deleteDirectory(File directory)
    {
        if (!directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectory(file);
                continue;
            }

            file.delete();
        }

        directory.delete();
    }

    /**
     * Add skin to the package in sub-directory "skins".
     *
     * @param skinName the name of the skin.
     * @param out the ZIP output stream where to put the skin.
     * @param context the XWiki context.
     * @throws IOException error when adding the skin to package.
     */
    private static void addSkinToZip(String skinName, ZipOutputStream out, Collection<String> exportedSkinFiles,
        XWikiContext context) throws IOException
    {
        File file = new File(context.getWiki().getEngineContext().getRealPath("/skins/" + skinName));

        // Don't include vm and LESS files by default
        FileFilter filter = new NotFileFilter(new SuffixFileFilter(new String[] { ".vm", ".less", "skin.properties" }));

        addDirToZip(file, filter, out, "skins" + ZIPPATH_SEPARATOR + skinName + ZIPPATH_SEPARATOR, exportedSkinFiles);
    }

    /**
     * Add a directory and all its sub-directories to the package.
     *
     * @param directory the directory to add.
     * @param filter the files to include or exclude from the copy
     * @param out the ZIP output stream where to put the skin.
     * @param basePath the path where to put the directory in the package.
     * @throws IOException error when adding the directory to package.
     */
    private static void addDirToZip(File directory, FileFilter filter, ZipOutputStream out, String basePath,
        Collection<String> exportedSkinFiles) throws IOException
    {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Adding dir [" + directory.getPath() + "] to the Zip file being generated.");
        }

        if (!directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles(filter);

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                addDirToZip(file, filter, out, basePath + file.getName() + ZIPPATH_SEPARATOR, exportedSkinFiles);
            } else {
                String path = basePath + file.getName();

                if (exportedSkinFiles != null && exportedSkinFiles.contains(path)) {
                    continue;
                }

                FileInputStream in = new FileInputStream(file);

                try {
                    // Starts a new Zip entry. It automatically closes the previous entry if present.
                    out.putNextEntry(new ZipEntry(path));

                    try {
                        IOUtils.copy(in, out);
                    } finally {
                        out.closeEntry();
                    }
                } finally {
                    in.close();
                }
            }
        }
    }
}
