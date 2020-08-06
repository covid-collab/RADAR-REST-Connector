package org.radarbase.connect.rest.fitbit.user.firebase;

import static com.google.firebase.cloud.FirestoreClient.getFirestore;
import static org.radarbase.connect.rest.fitbit.user.firebase.FirebaseFitbitAuthDetails.OAUTH_KEY;
import static org.radarbase.connect.rest.fitbit.user.firebase.FirebaseUserRepository.getDocument;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.QuerySnapshot;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.radarbase.connect.rest.fitbit.FitbitRestSourceConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Singleton class to hold the users from firestore. This is needed so we don't read the users
 * every time the user repository is instantiated. This only creates a new instance when the relevant
 * properties in the config have changed.
 * See {@link #getInstanceFor(FitbitRestSourceConnectorConfig)}
 */
public class CovidCollabFirestore {

  private static final Logger logger = LoggerFactory.getLogger(CovidCollabFirestore.class);
  private static CovidCollabFirestore _INSTANCE;
  private static FitbitRestSourceConnectorConfig restSourceConnectorConfig;
  private final ConcurrentHashMap<String, FirebaseUser> cachedUsers = new ConcurrentHashMap<>();
  private final CollectionReference userCollection;
  private final CollectionReference fitbitCollection;
  private final List<String> allowedUsers;
  private final List<String> excludedUsers;
  private ListenerRegistration fitbitCollectionListenerRegistration;
  private boolean hasPendingUpdates = true;

  private CovidCollabFirestore(FitbitRestSourceConnectorConfig fitbitConfig) {
    this.userCollection =
        getFirestore().collection(fitbitConfig.getFitbitUserRepositoryFirestoreUserCollection());
    this.fitbitCollection =
        getFirestore().collection(fitbitConfig.getFitbitUserRepositoryFirestoreFitbitCollection());
    this.allowedUsers = fitbitConfig.getFitbitUsers();
    this.excludedUsers = fitbitConfig.getExcludedFitbitUsers();

    /**
     * Currently, we only listen for the fitbit collection, as it contains most information while
     * the user collection only contains project Id which is not supposed to change. The user
     * document is pulled every time the corresponding fitbit document is pulled, so it will be
     * sufficiently upto date. Moreover, not every document in the user collection will have linked
     * the fitbit. In the future, we might listen to user collection too if required.
     */
    if (fitbitCollectionListenerRegistration==null) {
      fitbitCollectionListenerRegistration = initListener(fitbitCollection, this::onEvent);
      logger.info("Added listener to Fitbit collection for real-time updates.");
    }
  }

  /**
   * Only instantiate a new object of this class if the Firestore collections, the allowed users and
   * the excluded users properties have changed.
   */
  public synchronized static CovidCollabFirestore getInstanceFor(
      FitbitRestSourceConnectorConfig connectorConfig) {
    if (restSourceConnectorConfig!=null
        && configEquals(restSourceConnectorConfig, connectorConfig)) {
      restSourceConnectorConfig = connectorConfig;
      return _INSTANCE;
    }
    if (_INSTANCE==null) {
      restSourceConnectorConfig = connectorConfig;
      _INSTANCE = new CovidCollabFirestore(connectorConfig);
    }
    return _INSTANCE;
  }

  private static boolean configEquals(FitbitRestSourceConnectorConfig c1,
                                FitbitRestSourceConnectorConfig c2) {
    return c1.getFitbitUsers().equals(c2.getFitbitUsers())
        && c1.getFitbitUserRepositoryFirestoreFitbitCollection()
        .equals(c2.getFitbitUserRepositoryFirestoreFitbitCollection())
        && c1.getFitbitUserRepositoryFirestoreUserCollection()
        .equals(c2.getFitbitUserRepositoryFirestoreUserCollection())
        && c1.getExcludedFitbitUsers().equals(c2.getExcludedFitbitUsers())
        && c1.hasIntradayAccess() == c2.hasIntradayAccess();
  }

  public Collection<FirebaseUser> getUsers() {
    return new ConcurrentHashMap<>(cachedUsers).values();
  }

  public FirebaseUser getUser(String key) throws IOException {
    try {
      return cachedUsers.get(key);
    } catch (NullPointerException ex) {
      logger.warn("The requested user was not found in cache. Creating a new one.");
      return createUser(key);
    }
  }

  public boolean hasUpdates() {
    return hasPendingUpdates;
  }

  public void applyUpdates() throws IOException {
    if (this.hasUpdates()) {
      hasPendingUpdates = false;
    } else {
      throw new IOException(
          "No pending updates available. Try calling this method only when updates are available");
    }
  }

  private ListenerRegistration initListener(
      CollectionReference collectionReference, EventListener<QuerySnapshot> eventListener) {
    return collectionReference.addSnapshotListener(eventListener);
  }

  protected FirebaseUser createUser(String uuid) throws IOException {
    logger.debug("Creating user using uuid...");
    DocumentSnapshot fitbitDocumentSnapshot = getDocument(uuid, fitbitCollection);
    DocumentSnapshot userDocumentSnapshot = getDocument(uuid, userCollection);

    return createUser(userDocumentSnapshot, fitbitDocumentSnapshot);
  }

  protected FirebaseUser createUser(
      DocumentSnapshot userSnapshot, DocumentSnapshot fitbitSnapshot) {
    if (!fitbitSnapshot.contains(OAUTH_KEY)) {
      logger.warn(
          "The 'oauth' key for user {} in the fitbit document is not present. Skipping...",
          fitbitSnapshot.getId());
      return null;
    }

    // Get the fitbit document for the user which contains Auth Info
    FirebaseFitbitAuthDetails authDetails =
        fitbitSnapshot.toObject(FirebaseFitbitAuthDetails.class);
    // Get the user document for the user which contains User Details
    FirebaseUserDetails userDetails = userSnapshot.toObject(FirebaseUserDetails.class);

    logger.debug("Auth details: {}", authDetails);
    logger.debug("User Details: {}", userDetails);

    // if auth details are not available, skip this user.
    if (authDetails==null || authDetails.getOauth2Credentials()==null) {
      logger.warn(
          "The auth details for user {} in the database are not valid. Skipping...",
          fitbitSnapshot.getId());
      return null;
    }

    // If no user details found, create one with default project.
    if (userDetails==null) {
      userDetails = new FirebaseUserDetails();
    }

    FirebaseUser user = new FirebaseUser();
    user.setUuid(fitbitSnapshot.getId());
    user.setUserId(fitbitSnapshot.getId());
    user.setFitbitAuthDetails(authDetails);
    user.setFirebaseUserDetails(userDetails);
    return user;
  }

  private synchronized void updateUser(DocumentSnapshot fitbitDocumentSnapshot) {
    try {
      FirebaseUser user =
          createUser(
              getDocument(fitbitDocumentSnapshot.getId(), userCollection), fitbitDocumentSnapshot);
      logger.debug("User to be updated: {}", user);
      if (checkValidUser(user)) {
        FirebaseUser user1 = cachedUsers.put(user.getId(), user);
        if (user1==null) {
          logger.info("Created new User: {}", user.getId());
        } else {
          logger.info("Updated existing user: {}", user1);
          logger.debug("Updated user is: {}", user);
        }
        hasPendingUpdates = true;
      } else if (user!=null && !user.isComplete()) {
        logger.info("User is not complete, skipping...");
        removeUser(fitbitDocumentSnapshot);
      } else {
        logger.info("User cannot be processed due to constraints");
        removeUser(fitbitDocumentSnapshot);
      }
    } catch (IOException e) {
      logger.error(
          "The update of the user {} was not possible.", fitbitDocumentSnapshot.getId(), e);
    }
  }

  /**
   * We add the user based on the following conditions -
   * 1) The user is not null
   * 2) the user is allowed in the configuration
   * 3) the user is not excluded in the configuration
   * 4) The user has not auth errors signified by the auth_result key in firebase
   *
   * @param user The user to check for validity
   * @return true if user is valid, false otherwise.
   */
  private boolean checkValidUser(FirebaseUser user) {
    if (user!=null && user.getFitbitAuthDetails().getAuthResult()!=null) {
      logger.info("Http code: {}", user.getFitbitAuthDetails().getAuthResult().getHttpCode());
    }
    return user!=null
        && user.isComplete()
        && (allowedUsers.isEmpty() || allowedUsers.contains(user.getId()))
        && (excludedUsers.isEmpty() || !excludedUsers.contains(user.getId()))
        && (user.getFitbitAuthDetails().getAuthResult()==null
        || user.getFitbitAuthDetails().getAuthResult().getHttpCode()==200);
  }

  private void removeUser(DocumentSnapshot documentSnapshot) {
    FirebaseUser user = cachedUsers.remove(documentSnapshot.getId());
    if (user!=null) {
      logger.info("Removed User: {}:", user);
      hasPendingUpdates = true;
    }
  }

  private void onEvent(QuerySnapshot snapshots, FirestoreException e) {
    if (e!=null) {
      logger.warn("Listen for updates failed: " + e);
      return;
    }

    logger.info(
        "OnEvent Called: {}, {}",
        snapshots.getDocumentChanges().size(),
        snapshots.getDocuments().size());
    for (DocumentChange dc : snapshots.getDocumentChanges()) {
      try {
        logger.info("Type: {}", dc.getType());
        switch (dc.getType()) {
          case ADDED:
          case MODIFIED:
            this.updateUser(dc.getDocument());
            break;
          case REMOVED:
            this.removeUser(dc.getDocument());
          default:
            break;
        }
      } catch (Exception exc) {
        logger.warn(
            "Could not process document change event for document: {}",
            dc.getDocument().getId(),
            exc);
      }
    }
  }
}
