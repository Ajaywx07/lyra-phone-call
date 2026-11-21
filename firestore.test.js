const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CONTACT_DOC_ID = "contact_9876543210";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validPayload(uid = ALICE_UID) {
  return {
    userId: uid,
    name: "Ajay",
    phoneNumber: "+91 98765 43210",
    label: "Owner",
    priorityContact: true,
    ownerContact: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot list or write cloud contacts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cloud_contacts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cloud_contacts").doc(CONTACT_DOC_ID).set(validPayload(ALICE_UID))
  );
});

test("Authenticated user: can create, read, list, update, and delete own cloud contact", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_contacts").doc(CONTACT_DOC_ID);

  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("cloud_contacts").get());
  await assertSucceeds(
    docRef.update({
      name: "Ajay Kumar",
      priorityContact: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Alice cannot read, list, write, or delete Bob's cloud contacts", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("cloud_contacts")
      .doc(CONTACT_DOC_ID)
      .set(validPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDocRef = aliceDb.collection("users").doc(BOB_UID).collection("cloud_contacts").doc(CONTACT_DOC_ID);

  await assertFails(bobDocRef.get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("cloud_contacts").get());
  await assertFails(bobDocRef.set(validPayload(ALICE_UID)));
  await assertFails(bobDocRef.update({ name: "Hacked", updatedAt: serverTimestamp() }));
  await assertFails(bobDocRef.delete());
});

test("Identity spoofing & shadow fields rejected on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cloud_contacts").doc(CONTACT_DOC_ID);

  await assertFails(docRef.set(validPayload(BOB_UID)));

  await assertFails(
    docRef.set({
      ...validPayload(ALICE_UID),
      isAdmin: true,
    })
  );

  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));
  await assertFails(
    docRef.update({
      userId: BOB_UID,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    docRef.update({
      name: 12345,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Value poisoning: oversized strings and invalid doc ID rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cloud_contacts")
      .doc("bad$id!")
      .set(validPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cloud_contacts")
      .doc(CONTACT_DOC_ID)
      .set({
        ...validPayload(ALICE_UID),
        name: "A".repeat(200),
      })
  );
});
