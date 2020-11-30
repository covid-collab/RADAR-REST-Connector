package org.radarbase.connect.rest.fitbit.user.firebase;

import com.google.cloud.firestore.CollectionReference;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import javax.ws.rs.NotAuthorizedException;
import org.radarbase.connect.rest.RestSourceConnectorConfig;
import org.radarbase.connect.rest.fitbit.FitbitRestSourceConnectorConfig;
import org.radarbase.connect.rest.fitbit.user.User;
import org.radarbase.connect.rest.fitbit.user.firebase.exception.HttpException;
import org.radarbase.connect.rest.fitbit.user.firebase.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The User repository that supports the covid-collab application (https://covid-collab.org/). The
 * data is stored in Firebase Firestore. This user repository reads data from this source and
 * creates user objects. To ease the creation of objects a new {@link User} {@link FirebaseUser} is
 * created.
 *
 * <p>Structure in Firestore is :- 1) Fitbit Collection -> User Document(uuid) -> Fitbit Details. 2)
 * Users Collection -> User Document(uuid) -> User Details.
 *
 * <p>See {@link FirebaseFitbitAuthDetails} for the keys present in Ftibit Details for each User.
 * See {@link FirebaseUserDetails} for the keys present in User Details for each User.
 */
public class CovidCollabFirebaseUserRepository extends FirebaseUserRepository {

  protected static final String FITBIT_TOKEN_ENDPOINT = "https://api.fitbit.com/oauth2/token";
  private static final Logger logger =
      LoggerFactory.getLogger(CovidCollabFirebaseUserRepository.class);

  private CollectionReference fitbitCollection;
  private FitbitTokenService fitbitTokenService;
  private CovidCollabFirestore covidCollabFirestore;

  @Override
  public User get(String key) throws IOException {
    logger.debug("Get user from Covid Collab Firebase Repo");
    return covidCollabFirestore.getUser(key);
  }

  @Override
  public Stream<? extends User> stream() {
    return covidCollabFirestore.getUsers().stream();
  }

  @Override
  public String getAccessToken(User user) throws IOException, NotAuthorizedException {
    FirebaseUser firebaseUser = covidCollabFirestore.getUser(user.getId());
    if (firebaseUser==null) {
      throw new NoSuchElementException("User " + user + " is not present in this user repository.");
    }
    FitbitOAuth2UserCredentials credentials = firebaseUser
        .getFitbitAuthDetails().getOauth2Credentials();
    if (credentials==null || credentials.isAccessTokenExpired()) {
      return refreshAccessToken(user);
    }
    return credentials.getAccessToken();
  }

  @Override
  public String refreshAccessToken(User user)
      throws IOException, NotAuthorizedException {
    FirebaseUser firebaseUser = covidCollabFirestore.getUser(user.getId());
    if (firebaseUser==null) {
      throw new NoSuchElementException("User " + user + " is not present in this user repository.");
    }
    FirebaseFitbitAuthDetails authDetails = firebaseUser.getFitbitAuthDetails();

    logger.info("Refreshing token for User: {}", firebaseUser);
    if (!authDetails.getOauth2Credentials().hasRefreshToken()) {
      logger.error("No refresh Token present");
      throw new NotAuthorizedException("The user does not contain a refresh token");
    }

    FitbitOAuth2UserCredentials userCredentials;
    // Make call to fitbit to get new refresh and access token.
    logger.info("Requesting to refreshToken.");
    try {
      userCredentials =
          fitbitTokenService.refreshToken(authDetails.getOauth2Credentials().getRefreshToken());
      logger.debug("Token Refreshed.");
    } catch (UnauthorizedException ex) {
      AuthResult result = new AuthResult(ex.getResponse().getStatus(),
          ex.getMessage(), ex.getTime(), ex.getErrorDescription());
      authDetails.setAuthResult(result);
      updateDocument(fitbitCollection.document(user.getId()), authDetails);
      throw ex;
    } catch (HttpException ex) {
      if (ex.getAuthResult().getHttpCode() != 409) {
        // We don't update on 409 as another process already refreshed the token
        authDetails.setAuthResult(ex.getAuthResult());
        updateDocument(fitbitCollection.document(user.getId()), authDetails);
      }
      throw new NotAuthorizedException("Failed to Refresh Token", ex,
          ex.getAuthResult().getMessage());
    } catch (IOException ex) {
      AuthResult result = new AuthResult(500,
          ex.getMessage());
      result.setErrorDescription("An I/O error occurred when trying to refresh token.");
      authDetails.setAuthResult(result);
      updateDocument(fitbitCollection.document(user.getId()), authDetails);
      throw ex;
    }

    if (sanityCheckAuthData(userCredentials, user)) {
      AuthResult result = new AuthResult(200, "");
      authDetails.setOauth2Credentials(userCredentials);
      authDetails.setAuthResult(result);
      updateDocument(fitbitCollection.document(user.getId()), authDetails);
      return userCredentials.getAccessToken();
    } else {
      AuthResult result = new AuthResult(500, "The auth data or user data was not valid.");
      result.setErrorDescription("An error occurred when trying to validate auth data.");
      authDetails.setAuthResult(result);
      updateDocument(fitbitCollection.document(user.getId()), authDetails);
      throw new IOException("There was a problem refreshing the token.");
    }
  }

  private boolean sanityCheckAuthData(FitbitOAuth2UserCredentials credentials, User user) {
    return !user.getId().isEmpty()
        && credentials!=null
        && credentials.hasRefreshToken()
        && credentials.getAccessToken()!=null;
  }

  @Override
  public boolean hasPendingUpdates() {
    return covidCollabFirestore.hasUpdates();
  }

  @Override
  public void applyPendingUpdates() throws IOException {
    covidCollabFirestore.applyUpdates();
  }

  @Override
  public void initialize(RestSourceConnectorConfig config) {
    super.initialize(config);

    FitbitRestSourceConnectorConfig fitbitConfig = (FitbitRestSourceConnectorConfig) config;

    this.covidCollabFirestore = CovidCollabFirestore.getInstanceFor(fitbitConfig);

    this.fitbitCollection =
        getFirestore().collection(fitbitConfig.getFitbitUserRepositoryFirestoreFitbitCollection());

    this.fitbitTokenService =
        new FitbitTokenService(
            fitbitConfig.getFitbitClient(),
            fitbitConfig.getFitbitClientSecret(),
            FITBIT_TOKEN_ENDPOINT);
  }
}
