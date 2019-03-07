/*
 * Copyright © 2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.oracle;

import co.cask.DBRecord;
import co.cask.cdap.api.artifact.ArtifactSummary;
import co.cask.cdap.api.plugin.PluginClass;
import co.cask.cdap.api.plugin.PluginPropertyField;
import co.cask.cdap.datapipeline.DataPipelineApp;
import co.cask.cdap.proto.id.ArtifactId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.test.TestConfiguration;
import co.cask.db.batch.DatabasePluginTestBase;
import co.cask.db.batch.sink.ETLDBOutputFormat;
import co.cask.db.batch.source.DataDrivenETLDBInputFormat;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

public class OraclePluginTestBase extends DatabasePluginTestBase {
  protected static final ArtifactId DATAPIPELINE_ARTIFACT_ID = NamespaceId.DEFAULT.artifact("data-pipeline", "3.2.0");
  protected static final ArtifactSummary DATAPIPELINE_ARTIFACT = new ArtifactSummary("data-pipeline", "3.2.0");
  protected static final long CURRENT_TS = System.currentTimeMillis();
  private static final String DRIVER_CLASS = "oracle.jdbc.driver.OracleDriver";

  protected static final String JDBC_DRIVER_NAME = "oracle";
  protected static final String UI_NAME = "Oracle";

  protected static String connectionUrl;
  protected static final int YEAR;
  protected static boolean tearDown = true;
  private static int startCount;

  @ClassRule
  public static final TestConfiguration CONFIG = new TestConfiguration("explore.enabled", false);

  static {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(new Date(CURRENT_TS));
    YEAR = calendar.get(Calendar.YEAR);
  }

  protected static final Map<String, String> BASE_PROPS = ImmutableMap.<String, String>builder()
    .put("host", System.getProperty("oracle.host"))
    .put("port", System.getProperty("oracle.port"))
    .put("database", System.getProperty("oracle.database"))
    .put("user", System.getProperty("oracle.username"))
    .put("password", System.getProperty("oracle.password"))
    .put("jdbcPluginName", JDBC_DRIVER_NAME)
    .put(OracleConstants.DEFAULT_BATCH_VALUE, "10")
    .build();

  @BeforeClass
  public static void setupTest() throws Exception {
    if (startCount++ > 0) {
      return;
    }

    setupBatchArtifacts(DATAPIPELINE_ARTIFACT_ID, DataPipelineApp.class);

    addPluginArtifact(NamespaceId.DEFAULT.artifact(JDBC_DRIVER_NAME, "1.0.0"),
                      DATAPIPELINE_ARTIFACT_ID,
                      OracleSource.class, OracleSink.class, DBRecord.class, ETLDBOutputFormat.class,
                      DataDrivenETLDBInputFormat.class, DBRecord.class, OraclePostAction.class, OracleAction.class);

    Class<?> driverClass = Class.forName(DRIVER_CLASS);

    // add oracle 3rd party plugin
    PluginClass oracleDriver = new PluginClass("jdbc", JDBC_DRIVER_NAME, "oracle driver class",
                                           driverClass.getName(),
                                           null, Collections.<String, PluginPropertyField>emptyMap());
    addPluginArtifact(NamespaceId.DEFAULT.artifact("oracle-jdbc-connector", "1.0.0"),
                      DATAPIPELINE_ARTIFACT_ID,
                      Sets.newHashSet(oracleDriver), driverClass);

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

    connectionUrl = "jdbc:oracle:thin:@" + BASE_PROPS.get("host") + ":" +
      BASE_PROPS.get("port") + ":" + BASE_PROPS.get("database");
    Connection conn = createConnection();
    createTestTables(conn);
    prepareTestData(conn);
  }

  protected static void createTestTables(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      // create a table that the action will truncate at the end of the run
      stmt.execute("CREATE TABLE dbActionTest (x int, day varchar(10))");
      // create a table that the action will truncate at the end of the run
      stmt.execute("CREATE TABLE postActionTest (x int, day varchar(10))");

      stmt.execute("CREATE TABLE my_table (" +
                     "  ID INT NOT NULL, " +
                     "  CHAR_COL CHAR(10)," +
                     "  CHARACTER_COL CHARACTER(10)," +
                     "  VARCHAR_COL VARCHAR(10)," +
                     "  VARCHAR2_COL VARCHAR2(10)," +
                     "  INT_COL INT," +
                     "  INTEGER_COL INTEGER," +
                     "  DEC_COL DEC," +
                     "  DECIMAL_COL DECIMAL(10, 2)," +
                     "  NUMBER_COL NUMBER(10, 2)," +
                     "  NUMERIC_COL NUMERIC(10, 2)," +
                     "  SMALLINT_COL SMALLINT," +
                     "  REAL_COL REAL," +
                     "  DATE_COL DATE," +
                     "  TIMESTAMP_COL TIMESTAMP," +
                     "  TIMESTAMPTZ_COL TIMESTAMP WITH TIME ZONE," +
                     "  TIMESTAMPLTZ_COL TIMESTAMP WITH LOCAL TIME ZONE," +
                     "  INTERVAL_YEAR_TO_MONTH_COL INTERVAL YEAR(3) TO MONTH," +
                     "  INTERVAL_DAY_TO_SECOND_COL INTERVAL DAY(2) TO SECOND," +
                     "  RAW_COL RAW(16)," +
                     "  CLOB_COL CLOB," +
                     "  BLOB_COL BLOB" +
                     ")");
      stmt.execute("CREATE TABLE MY_DEST_TABLE AS " +
                     "SELECT * FROM my_table");
      stmt.execute("CREATE TABLE your_table AS " +
                     "SELECT * FROM my_table");
    }
  }

  protected static void prepareTestData(Connection conn) throws SQLException {

    try (
      Statement stmt = conn.createStatement();
      PreparedStatement pStmt1 =
        conn.prepareStatement("INSERT INTO my_table " +
                                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?," +
                                "       ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      PreparedStatement pStmt2 =
        conn.prepareStatement("INSERT INTO your_table " +
                                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?," +
                                "       ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

      stmt.execute("insert into dbActionTest values (1, '1970-01-01')");
      stmt.execute("insert into postActionTest values (1, '1970-01-01')");

      populateData(pStmt1, pStmt2);
    }
  }

  private static void populateData(PreparedStatement ...stmts) throws SQLException {
    // insert the same data into both tables: my_table and your_table
    for (PreparedStatement pStmt : stmts) {
      for (int i = 1; i <= 5; i++) {
        Clob clob = null;
        try {
          String name = "user" + i;
          pStmt.setInt(1, i);
          pStmt.setString(2, name);
          pStmt.setString(3, name);
          pStmt.setString(4, name);
          pStmt.setString(5, name);
          pStmt.setInt(6, 42 + i);
          pStmt.setInt(7, 24 + i);
          pStmt.setBigDecimal(8, new BigDecimal(54.56 + i));
          pStmt.setBigDecimal(9, new BigDecimal(54.65 + i));
          pStmt.setBigDecimal(10, new BigDecimal(32.65 + i));
          pStmt.setBigDecimal(11, new BigDecimal(23.65 + i));
          pStmt.setInt(12, i);
          pStmt.setFloat(13, (float) 14.45 + i);
          pStmt.setDate(14, new Date(CURRENT_TS));
          pStmt.setTimestamp(15, new Timestamp(CURRENT_TS));
          pStmt.setTimestamp(16, new Timestamp(CURRENT_TS));
          pStmt.setTimestamp(17, new Timestamp(CURRENT_TS));
          pStmt.setString(18, "300-5");
          pStmt.setString(19, "23 3:02:10");
          pStmt.setBytes(20, name.getBytes());

          clob = pStmt.getConnection().createClob();
          clob.setString(1, name);
          pStmt.setClob(21, clob);

          pStmt.setBytes(22, name.getBytes());
          pStmt.executeUpdate();
        } finally {
          if (Objects.nonNull(clob)) {
            clob.free();
          }
        }
      }
    }
  }

  public static Connection createConnection() {
    try {
      Class.forName(DRIVER_CLASS);
      return DriverManager.getConnection(connectionUrl, BASE_PROPS.get("user"), BASE_PROPS.get("password"));
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  @AfterClass
  public static void tearDownDB() throws SQLException {
    if (!tearDown) {
      return;
    }

    try (Connection conn = createConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE my_table");
      stmt.execute("DROP TABLE your_table");
      stmt.execute("DROP TABLE postActionTest");
      stmt.execute("DROP TABLE dbActionTest");
      stmt.execute("DROP TABLE MY_DEST_TABLE");
    }
  }
}