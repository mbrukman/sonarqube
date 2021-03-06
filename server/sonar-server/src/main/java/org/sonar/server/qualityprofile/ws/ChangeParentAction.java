/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.server.qualityprofile.ws;

import org.sonar.api.resources.Languages;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.api.server.ws.WebService.NewController;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.qualityprofile.QualityProfileDto;
import org.sonar.server.qualityprofile.QProfileFactory;
import org.sonar.server.qualityprofile.QProfileRef;
import org.sonar.server.qualityprofile.RuleActivator;
import org.sonar.server.user.UserSession;

import static org.apache.commons.lang.StringUtils.isEmpty;

public class ChangeParentAction implements QProfileWsAction {

  private static final String PARAM_PARENT_KEY = "parentKey";
  private static final String PARAM_PARENT_NAME = "parentName";

  private DbClient dbClient;
  private final RuleActivator ruleActivator;
  private final QProfileFactory profileFactory;
  private final Languages languages;
  private final UserSession userSession;

  public ChangeParentAction(DbClient dbClient, RuleActivator ruleActivator, QProfileFactory profileFactory,
    Languages languages, UserSession userSession) {
    this.dbClient = dbClient;
    this.ruleActivator = ruleActivator;
    this.profileFactory = profileFactory;
    this.languages = languages;
    this.userSession = userSession;
  }

  @Override
  public void define(NewController context) {
    NewAction inheritance = context.createAction("change_parent")
      .setSince("5.2")
      .setPost(true)
      .setDescription("Change a quality profile's parent.")
      .setHandler(this);

    QProfileRef.defineParams(inheritance, languages);

    inheritance.createParam(PARAM_PARENT_KEY)
      .setDescription("The key of the new parent profile. If this parameter is set, parentName must not be set. " +
        "If both are left empty, the inheritance link with current parent profile (if any) is broken, which deactivates all rules " +
        "which come from the parent and are not overridden. Require Administer Quality Profiles permission.")
      .setExampleValue("sonar-way-java-12345");
    inheritance.createParam(PARAM_PARENT_NAME)
      .setDescription("A quality profile name. If this parameter is set, profileKey must not be set and language must be set to disambiguate.")
      .setExampleValue("Sonar way");

  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn().checkPermission(GlobalPermissions.QUALITY_PROFILE_ADMIN);

    try (DbSession dbSession = dbClient.openSession(false)) {
      QualityProfileDto profile = profileFactory.find(dbSession, QProfileRef.from(request));
      String parentKey = request.param(PARAM_PARENT_KEY);
      String parentName = request.param(PARAM_PARENT_NAME);
      if (isEmpty(parentKey) && isEmpty(parentName)) {
        ruleActivator.setParent(dbSession, profile.getKey(), null);
      } else {
        QProfileRef parentRef = QProfileRef.from(parentKey, request.param(QProfileRef.PARAM_LANGUAGE), parentName);
        QualityProfileDto parent = profileFactory.find(dbSession, parentRef);
        ruleActivator.setParent(dbSession, profile.getKey(), parent.getKey());
      }
      response.noContent();
    }
  }
}
