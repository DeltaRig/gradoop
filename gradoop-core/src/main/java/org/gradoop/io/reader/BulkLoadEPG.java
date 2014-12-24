package org.gradoop.io.reader;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.gradoop.model.Vertex;
import org.gradoop.storage.hbase.VertexHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * HBase bulk import.
 * <p/>
 * Reads import data using a given
 * {@link org.gradoop.io.reader.VertexLineReader}
 * and creates the corresponding Puts using a given
 * {@link org.gradoop.storage.hbase.VertexHandler}.
 */
public class BulkLoadEPG extends
  Mapper<LongWritable, Text, ImmutableBytesWritable, Put> {

  /**
   * Configuration key for the vertex line reader.
   */
  public static final String VERTEX_LINE_READER =
    BulkLoadEPG.class.getName() + ".vertex_line_reader";
  /**
   * Configuration key for the vertex handler.
   */
  public static final String VERTEX_HANDLER =
    BulkLoadEPG.class.getName() + ".vertex_handler";
  /**
   * Used to convert an input line to a {@link org.gradoop.model.Vertex}.
   */
  private VertexLineReader vertexLineReader;
  /**
   * Used to create a HBase Put from a given {@link org.gradoop.model.Vertex}.
   */
  private VertexHandler vertexHandler;

  /**
   * {@inheritDoc}
   */
  @Override
  protected void setup(Context context) throws IOException,
    InterruptedException {
    Configuration conf = context.getConfiguration();

    Class<? extends VertexLineReader> readerClass =
      conf.getClass(VERTEX_LINE_READER, null, VertexLineReader.class);

    Class<? extends VertexHandler> handlerClass =
      conf.getClass(VERTEX_HANDLER, null, VertexHandler.class);

    try {
      this.vertexLineReader = readerClass.getConstructor().newInstance();
      this.vertexHandler = handlerClass.getConstructor().newInstance();
    } catch (NoSuchMethodException | InstantiationException |
      InvocationTargetException | IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void map(LongWritable key, Text value, Context context) throws
    IOException, InterruptedException {
    boolean readerHasListSupport = vertexLineReader.supportsVertexLists();
    if (readerHasListSupport) {
      for (Vertex v : vertexLineReader.readVertexList(value.toString())) {
        addVertexToContext(context, v);
      }
    } else {
      addVertexToContext(context,
        vertexLineReader.readVertex(value.toString()));
    }
  }

  /**
   * Adds the given vertex to the output of the map function.
   *
   * @param context map context
   * @param vertex  vertex to be added to context
   * @throws IOException
   * @throws InterruptedException
   */
  private void addVertexToContext(Context context, Vertex vertex) throws
    IOException, InterruptedException {
    byte[] vertexID = vertexHandler.getRowKey(vertex.getID());
    ImmutableBytesWritable outKey = new ImmutableBytesWritable(vertexID);
    Put put = new Put(vertexID);
    put = vertexHandler.writeVertex(put, vertex);
    context.write(outKey, put);
  }
}
