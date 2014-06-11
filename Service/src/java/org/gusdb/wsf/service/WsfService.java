/**
 * 
 */
package org.gusdb.wsf.service;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.log4j.Logger;
import org.gusdb.wsf.plugin.PluginExecutor;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.WsfException;

/**
 * The WSF Web service entry point.
 * 
 * @author Jerric
 * @created Nov 2, 2005
 */
@Path("/")
public class WsfService {
  
  public static final String VERSION = "3.0.0";

  public static final String CONFIG_FILE = "wsf-config.xml";
  public static final String PARAM_REQUEST = "request";

  private static final Logger LOG = Logger.getLogger(WsfService.class);

  public WsfService() {
    // set up the config dir
    // String gusHome = System.getProperty("GUS_HOME");
    // if (gusHome != null) {
    // String configPath = gusHome + "/config/";
    // STATIC_CONTEXT.put(Plugin.CTX_CONFIG_PATH, configPath);
    // }
    LOG.debug("WsfService initialized");
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response invoke(@FormParam(PARAM_REQUEST) final String jsonRequest) {
    long start = System.currentTimeMillis();

    LOG.debug("Invoking " + jsonRequest);

    // open a StreamingOutput
    StreamingOutput output = new StreamingOutput() {

      @Override
      public void write(OutputStream outStream) throws IOException {
        // prepare to run the plugin
        PluginExecutor executor = new PluginExecutor();
        ResponseStatus status = new ResponseStatus();
        ObjectOutputStream objectStream = new ObjectOutputStream(outStream);
        try {
          WsfRequest request = new WsfRequest(jsonRequest);

          // prepare response
          PluginResponse pluginResponse = new StreamingPluginResponse(objectStream);

          // invoke plugin
          int signal = executor.execute(request.getPluginClass(), request, pluginResponse);
          status.setSignal(signal);
        }
        catch (WsfException ex) {
          status.setSignal(-1);
          status.setException(ex);
        }
        finally {
          // send signal back
          objectStream.writeObject(status);
          objectStream.flush();
          objectStream.close();

          LOG.debug("Status returned: " + status);
        }
      }
    };

    // get the response
    Response response = Response.ok(output).build();
    long end = System.currentTimeMillis();
    LOG.info("WsfService call finished in " + ((end - start) / 1000D) + " seconds");
    return response;
  }
  
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String getInfo() {
    return this.getClass().getSimpleName() + " version " + VERSION;
  }
}
