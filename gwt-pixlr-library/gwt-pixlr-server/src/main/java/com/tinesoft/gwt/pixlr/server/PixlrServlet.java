
package com.tinesoft.gwt.pixlr.server;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract servlet that parses each request sent by 'Pixlr' after user has saved an image, and
 * provides the information about the saved image via a {@link PixlrResult} object.
 * <p>
 * Concrete implementations just have to implement the {@link #handlePixlrResult(PixlrResult)} to
 * handle the saved image and additional parameters that may have been passed.
 * 
 * @author Tine Kondo
 * @version $Id$
 */
public abstract class PixlrServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PixlrServlet.class);

    /**
     * The content of this parameter depends on the value of the "method" in-parameter. If method
     * was GET the value of image is an url to where we saved the image. If method was POST the
     * value of image is the actual image.
     */
    private static final String IMAGE_PARAM = "image";
    /**
     * The title of the image the user typed in when saving.
     */
    private static final String TITLE_PARAM = "title";
    /**
     * The type of image can be: jpg, png, bmp or pxd.
     */
    private static final String TYPE_PARAM = "type";
    /**
     * The state of the image, can be "new", "copy", "replace". New is when the user open/creates
     * the image in the editor. Copy is when the image is from the API and the image checked
     * "Save as copy".
     */
    private static final String STATE_PARAM = "state";

    /**
     * {@link AbstractPixlrServlet}'s init-param defining the max request size allowed.
     */
    private static final String MAX_REQUEST_SIZE_INIT_PARAM = "maxRequestSize";

    /**
     * 
     */
    public static final String PIXLR_RESULT = "com.tinesoft.gwt.pixlr.server.result";
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    protected Integer maxRequestSize = null;

    @SuppressWarnings("unchecked")
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

        LOG.info("New GET request received from URI:'{}'", request.getRequestURL());

        final Map<String, String[]> params = request.getParameterMap();

        final PixlrResult result = new PixlrResult();

        for (final Map.Entry<String, String[]> entry : params.entrySet()) {
            processRequestParameter(entry, result);

        }
        LOG.debug("Parsed result from Pixlr: [title={}, state={}, type={}, additionalParameters={}]", //
                  new Object[] { result.getTitle(), result.getState(),//
                          result.getType(), result.getAdditionalParameters() });
        // Let the concrete implementation handles the result from 'Pixlr'
        handlePixlrResult(result);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        try {

            LOG.info("New POST request received from URI:'{}'", request.getRequestURL());

            // Check that we have a file upload request
            final boolean isMultipart = ServletFileUpload.isMultipartContent(request);
            if (!isMultipart) {
                LOG.error("Cannot process request. No file to upload.");
                return;
            }

            // Create a new file upload handler
            final ServletFileUpload upload = new ServletFileUpload();

            // Set overall request size constraint
            if (maxRequestSize != null) {
                upload.setSizeMax(maxRequestSize);
            }

            // Parse the request
            final FileItemIterator iter = upload.getItemIterator(request);

            final PixlrResult result = new PixlrResult();

            while (iter.hasNext()) {
                final FileItemStream item = iter.next();
                if (item.isFormField()) {
                    // Process regular form field (input type="text|radio|checkbox|select,etc).
                    processFormParameter(item, result);
                } else {
                    // Process form file field (input type="file").
                    processUploadedParameter(item, result);
                }
            }

            LOG.debug("Parsed result from Pixlr: [title={}, state={}, type={}, additionalParameters={}]",
                      new Object[] { result.getTitle(), result.getState(),//
                              result.getType(), result.getAdditionalParameters() });

            // Let the concrete implementation handles the result from 'Pixlr'
            handlePixlrResult(result);

        } catch (final FileUploadException e) {
            throw new ServletException("Cannot parse multipart request.", e);
        }
    }

    /**
     * Abstract method called after finishing parsing the request sent by Pixlr each time the user
     * saves its changes on the image being edited.
     * 
     * <p>
     * This allows you to easily handle the returned image information (for example: save image to
     * database , to file system, etc).
     * </p>
     * 
     * @param result a convenient {@link PixlrResult} object, containing the information about the
     *            edited image, as well as any additional parameter you may have sent when calling
     *            the service
     */
    protected abstract void handlePixlrResult(PixlrResult result);

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final String reqSize = config.getInitParameter(MAX_REQUEST_SIZE_INIT_PARAM);

        if (reqSize != null) {
            maxRequestSize = Integer.parseInt(reqSize);
        }

    }

    private void processFormParameter(final FileItemStream fileItem, final PixlrResult result) throws IOException {

        assert result != null : "processFormField(): 'resut' cannot be null!";
        assert fileItem.isFormField() : "processFormField(): 'fileItem' must be a form field!";

        final String fieldName = fileItem.getFieldName();
        final String fieldValue = Streams.asString(fileItem.openStream());

        if (TITLE_PARAM.equals(fieldName)) {
            result.setTitle(fieldValue);
        } else if (TYPE_PARAM.equals(fieldName)) {
            result.setType(PixlrImageType.from(fieldValue));
        } else if (STATE_PARAM.equals(fieldName)) {
            result.setState(PixlrImageState.from(fieldValue));
        } else {
            result.putAdditionalParameter(fieldName, fieldValue);
        }

    }

    private void processRequestParameter(final Map.Entry<String, String[]> fieldEntry, final PixlrResult result) throws IOException {

        assert result != null : "processRequestParamter(): 'resut' cannot be null!";
        assert fieldEntry != null : "processRequestParamter(): 'fieldEntry' cannot be null!";

        final String fieldName = fieldEntry.getKey();
        final String fieldValue = fieldEntry.getValue()[0];

        if (IMAGE_PARAM.equals(fieldName)) {
            // 'fiedlValue' contains the URL to the image
            final URL url = new URL(fieldValue);
            final URLConnection urlConnection = url.openConnection();
            result.setImage(urlConnection.getInputStream());
        } else if (TITLE_PARAM.equals(fieldName)) {
            result.setTitle(fieldValue);
        } else if (TYPE_PARAM.equals(fieldName)) {
            result.setType(PixlrImageType.from(fieldValue));
        } else if (STATE_PARAM.equals(fieldName)) {
            result.setState(PixlrImageState.from(fieldValue));
        } else {
            result.putAdditionalParameter(fieldName, fieldValue);
        }

    }

    private void processUploadedParameter(final FileItemStream fileItem, final PixlrResult result) throws IOException {

        assert result != null : "processUploadedField(): 'resut' cannot be null!";
        assert !fileItem.isFormField() : "processUploadedField(): 'fileItem' cannot be a form field!";

        final String fieldName = fileItem.getFieldName();

        if (IMAGE_PARAM.equals(fieldName)) {
            result.setImage(fileItem.openStream());
        }

    }
}
