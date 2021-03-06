package org.radarbase.connect.rest.fitbit.user.firebase;

import com.google.cloud.firestore.annotation.Exclude;
import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import com.google.cloud.firestore.annotation.PropertyName;
import java.time.Instant;
import java.util.UUID;

/**
 * POJO corresponding to the Fitbit Auth details document for a user in Firestore. Currently,
 * consists of OAuth 2 details, sourceId and date range for data collection.
 */
@IgnoreExtraProperties
public class FirebaseFitbitAuthDetails {

  public static final String OAUTH_KEY = "oauth";

  protected static final String DEFAULT_SOURCE_ID = "fitbit";
  private String sourceId = getDefaultSourceId();
  private Long startDate;
  private Long endDate;
  private Long version;

  private FitbitOAuth2UserCredentials oauth2Credentials;
  private AuthResult authResult;

  public FirebaseFitbitAuthDetails() {
    this.oauth2Credentials = new FitbitOAuth2UserCredentials();
    this.startDate = Instant.parse("2017-01-01T00:00:00Z").toEpochMilli();
    this.endDate = Instant.parse("9999-12-31T23:59:59.999Z").toEpochMilli();
    this.version = null;
    this.authResult = null;
  }

  @Exclude
  protected static String getDefaultSourceId() {
    return DEFAULT_SOURCE_ID + "-" + UUID.randomUUID();
  }

  @PropertyName("version")
  public Long getVersion() {
    return version;
  }

  @PropertyName("version")
  public void setVersion(Long version) {
    if (version!=null) {
      this.version = version;
    }
  }

  @PropertyName("start_date")
  public Long getStartDate() {
    return startDate;
  }

  @PropertyName("start_date")
  public void setStartDate(Long startDate) {
    if (startDate!=null) {
      this.startDate = startDate;
    }
  }

  @PropertyName("auth_result")
  public AuthResult getAuthResult() {
    return authResult;
  }

  @PropertyName("auth_result")
  public void setAuthResult(AuthResult authResult) {
    this.authResult = authResult;
  }

  @PropertyName("end_date")
  public Long getEndDate() {
    return endDate;
  }

  @PropertyName("end_date")
  public void setEndDate(Long endDate) {
    if (endDate!=null) {
      this.endDate = endDate;
    }
  }

  @PropertyName("source_id")
  public String getSourceId() {
    return sourceId;
  }

  @PropertyName("source_id")
  public void setSourceId(String sourceId) {
    if (sourceId!=null && !sourceId.trim().isEmpty()) {
      this.sourceId = sourceId;
    }
  }

  @PropertyName(OAUTH_KEY)
  public FitbitOAuth2UserCredentials getOauth2Credentials() {
    return oauth2Credentials;
  }

  @PropertyName(OAUTH_KEY)
  public void setOauth2Credentials(FitbitOAuth2UserCredentials oauth2Credentials) {
    this.oauth2Credentials = oauth2Credentials;
  }

  @Exclude
  @Override
  public String toString() {
    return "FirebaseFitbitAuthDetails{" +
        "sourceId='" + sourceId + '\'' +
        ", startDate=" + startDate +
        ", endDate=" + endDate +
        ", version=" + version +
        ", oauth2Credentials=" + "***hidden***" +
        ", authResult=" + authResult +
        '}';
  }
}
