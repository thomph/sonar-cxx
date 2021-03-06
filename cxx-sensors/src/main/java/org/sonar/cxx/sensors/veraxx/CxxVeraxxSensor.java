/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2020 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.cxx.sensors.veraxx;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.cxx.sensors.utils.CxxIssuesReportSensor;
import org.sonar.cxx.sensors.utils.CxxUtils;
import org.sonar.cxx.sensors.utils.EmptyReportException;
import org.sonar.cxx.sensors.utils.StaxParser;
import org.sonar.cxx.utils.CxxReportIssue;

/**
 * {@inheritDoc}
 */
public class CxxVeraxxSensor extends CxxIssuesReportSensor {

  public static final String REPORT_PATH_KEY = "sonar.cxx.vera.reportPath";
  private static final Logger LOG = Loggers.get(CxxVeraxxSensor.class);

  public static List<PropertyDefinition> properties() {
    return Collections.unmodifiableList(Arrays.asList(
      PropertyDefinition.builder(REPORT_PATH_KEY)
        .name("Vera++ XML report(s)")
        .description("Path to <a href='https://bitbucket.org/verateam'>Vera++</a> XML reports(s),"
                       + " relative to projects root." + USE_ANT_STYLE_WILDCARDS)
        .category("CXX External Analyzers")
        .subCategory("Vera++")
        .onQualifiers(Qualifiers.PROJECT)
        .multiValues(true)
        .build()
    ));
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("CXX Vera++ report import")
      .onlyOnLanguage("c++")
      .createIssuesForRuleRepository(getRuleRepositoryKey())
      .onlyWhenConfiguration(conf -> conf.hasKey(getReportPathKey()));
  }

  @Override
  protected void processReport(final SensorContext context, File report)
    throws javax.xml.stream.XMLStreamException {
    LOG.debug("Processing 'Vera++' format");
    try {
      var parser = new StaxParser((SMHierarchicCursor rootCursor) -> {
        try {
          rootCursor.advance();
        } catch (com.ctc.wstx.exc.WstxEOFException eofExc) {
          throw new EmptyReportException("Cannot read vera++ report ", eofExc);
        }

        SMInputCursor fileCursor = rootCursor.childElementCursor("file");
        while (fileCursor.getNext() != null) {
          String name = fileCursor.getAttrValue("name");

          SMInputCursor errorCursor = fileCursor.childElementCursor("error");
          while (errorCursor.getNext() != null) {
            if (!"error".equals(name)) {
              String line = errorCursor.getAttrValue("line");
              String message = errorCursor.getAttrValue("message");
              String source = errorCursor.getAttrValue("source");

              var issue = new CxxReportIssue(source, name, line, message);
              saveUniqueViolation(context, issue);
            } else {
              LOG.debug("Error in file '{}', with message '{}'",
                        name + "(" + errorCursor.getAttrValue("line") + ")",
                        errorCursor.getAttrValue("message"));
            }
          }
        }
      });

      parser.parse(report);
    } catch (com.ctc.wstx.exc.WstxUnexpectedCharException e) {
      LOG.error("Ignore XML error from Veraxx '{}'", CxxUtils.getStackTrace(e));
    }
  }

  @Override
  protected String getReportPathKey() {
    return REPORT_PATH_KEY;
  }

  @Override
  protected String getRuleRepositoryKey() {
    return CxxVeraxxRuleRepository.KEY;
  }

}
