const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const ADMIN_EMAIL = "farahrifa58@gmail.com";
const OTHER_UID = "buyer_uid_002";
const OTHER_EMAIL = "otheruser@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminDb() {
  return testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function spoofedAdminDb() {
  return testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
}

function nonAdminDb() {
  return testEnv
    .authenticatedContext(OTHER_UID, {
      email: OTHER_EMAIL,
      email_verified: true,
    })
    .firestore();
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

test("Unauthenticated user: denied all reads and writes on activation_codes and users", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("activation_codes").get());
  await assertFails(unauth.collection("users").get());
  await assertFails(
    unauth.collection("activation_codes").doc("JUALAN-AB12-CD34").set({
      status: "available",
      createdByUid: ADMIN_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing (unverified admin email) & non-admin user: denied access", async () => {
  const spoofed = spoofedAdminDb();
  const buyer = nonAdminDb();

  await assertFails(spoofed.collection("activation_codes").get());
  await assertFails(spoofed.collection("users").get());
  await assertFails(
    spoofed.collection("activation_codes").doc("JUALAN-SP00-F001").set({
      status: "available",
      createdByUid: "spoof_uid",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(buyer.collection("activation_codes").get());
  await assertFails(buyer.collection("users").get());
  await assertFails(
    buyer.collection("activation_codes").doc("JUALAN-BY00-R001").set({
      status: "available",
      createdByUid: OTHER_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Verified admin: create activation_code with exact Kotlin payload succeeds; extra keys or spoofed UID fail", async () => {
  const db = adminDb();
  const nowPast = new Date(Date.now() - 1000);

  // Exact Kotlin create payload: status, createdByUid, createdAt
  await assertSucceeds(
    db.collection("activation_codes").doc("JUALAN-A7K9-M2P4").set({
      status: "available",
      createdByUid: ADMIN_UID,
      createdAt: nowPast,
    })
  );

  // Verify admin can list activation_codes
  await assertSucceeds(db.collection("activation_codes").get());

  // Reject ghost/extra key (Shadow Write Test)
  await assertFails(
    db.collection("activation_codes").doc("JUALAN-GHOST-1234").set({
      status: "available",
      createdByUid: ADMIN_UID,
      createdAt: nowPast,
      id: "JUALAN-GHOST-1234",
    })
  );

  // Reject creating code directly with status 'used'
  await assertFails(
    db.collection("activation_codes").doc("JUALAN-USED-1234").set({
      status: "used",
      createdByUid: ADMIN_UID,
      createdAt: nowPast,
    })
  );

  // Reject spoofed createdByUid
  await assertFails(
    db.collection("activation_codes").doc("JUALAN-FAKE-9999").set({
      status: "available",
      createdByUid: OTHER_UID,
      createdAt: nowPast,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    db.collection("activation_codes").doc("JUALAN-FUTR-8888").set({
      status: "available",
      createdByUid: ADMIN_UID,
      createdAt: new Date(Date.now() + 60000),
    })
  );
});

test("Verified admin: can delete available code but cannot delete used code", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const raw = ctx.firestore();
    await raw.collection("activation_codes").doc("JUALAN-AVAL-1111").set({
      status: "available",
      createdByUid: ADMIN_UID,
      createdAt: new Date(Date.now() - 5000),
    });
    await raw.collection("activation_codes").doc("JUALAN-USED-2222").set({
      status: "used",
      createdByUid: ADMIN_UID,
      createdAt: new Date(Date.now() - 5000),
      businessName: "Toko Maju",
      usedByEmail: OTHER_EMAIL,
    });
  });

  const db = adminDb();
  await assertSucceeds(db.collection("activation_codes").doc("JUALAN-AVAL-1111").delete());
  await assertFails(db.collection("activation_codes").doc("JUALAN-USED-2222").delete());
});

test("Verified admin: can list users and reset registeredDeviceId & registeredDeviceIds on users and activation_codes; shadow updates fail", async () => {
  const created = new Date(Date.now() - 20000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const raw = ctx.firestore();
    await raw.collection("users").doc(OTHER_UID).set({
      businessName: "Warung Berkah",
      email: OTHER_EMAIL,
      activationCode: "LAPAK-USED-2222",
      registeredDeviceId: "android-device-xyz-99",
      registeredDeviceIds: ["android-device-xyz-99", "android-device-abc-11"],
      createdAt: created,
      updatedAt: created,
    });
    await raw.collection("activation_codes").doc("LAPAK-USED-2222").set({
      status: "used",
      createdByUid: ADMIN_UID,
      createdAt: created,
      registeredDeviceId: "android-device-xyz-99",
      registeredDeviceIds: ["android-device-xyz-99", "android-device-abc-11"],
    });
  });

  const db = adminDb();
  await assertSucceeds(db.collection("users").get());

  // Exact Kotlin Reset Perangkat update payload on users/{uid}
  await assertSucceeds(
    db.collection("users").doc(OTHER_UID).update({
      registeredDeviceId: "",
      registeredDeviceIds: [],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Exact Kotlin Reset Perangkat update payload on activation_codes/{kode}
  await assertSucceeds(
    db.collection("activation_codes").doc("LAPAK-USED-2222").update({
      registeredDeviceId: "",
      registeredDeviceIds: [],
    })
  );

  // Reject shadow update trying to mutate email or extra ghost field
  await assertFails(
    db.collection("users").doc(OTHER_UID).update({
      registeredDeviceId: "",
      registeredDeviceIds: [],
      updatedAt: new Date(Date.now() - 1000),
      isAdmin: true,
    })
  );

  // Reject value poisoning (non-string registeredDeviceId or non-list registeredDeviceIds)
  await assertFails(
    db.collection("users").doc(OTHER_UID).update({
      registeredDeviceId: 12345,
      registeredDeviceIds: [],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    db.collection("users").doc(OTHER_UID).update({
      registeredDeviceId: "",
      registeredDeviceIds: "not-a-list",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    db.collection("activation_codes").doc("LAPAK-USED-2222").update({
      registeredDeviceId: "",
      registeredDeviceIds: [12345],
    })
  );

  // Reject deleting buyer user
  await assertFails(db.collection("users").doc(OTHER_UID).delete());

  // Non-admin user can read own profile only, not list users or reset device
  const buyer = nonAdminDb();
  await assertSucceeds(buyer.collection("users").doc(OTHER_UID).get());
  await assertFails(buyer.collection("users").get());
  await assertFails(
    buyer.collection("users").doc(OTHER_UID).update({
      registeredDeviceId: "",
      registeredDeviceIds: [],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    buyer.collection("activation_codes").doc("LAPAK-USED-2222").update({
      registeredDeviceId: "",
      registeredDeviceIds: [],
    })
  );
});
