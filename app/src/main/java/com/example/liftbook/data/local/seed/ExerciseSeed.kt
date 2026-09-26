package com.example.liftbook.data.local.seed

import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import com.example.liftbook.domain.model.Equipment as E
import com.example.liftbook.domain.model.ExerciseType as T
import com.example.liftbook.domain.model.MuscleGroup as M

/**
 * The built-in exercise library (FR-1.1), inserted once when the database is created.
 *
 * Ids are fixed forever. They were generated once as name-based UUIDs and are now plain data:
 * renaming an exercise here must not change its id, because workouts, exports and other
 * devices refer to it by id. Names follow "Movement (Equipment)" when a movement has several
 * equipment variants, so variants sort next to each other.
 *
 * The type decides what a set records (FR-3.3), which is why timed holds such as Plank are
 * CARDIO: it is the type that logs a duration.
 *
 * Adding exercises after release needs a migration that inserts them — this list is only
 * applied to a fresh database.
 */
object ExerciseSeed {

    internal val exercises: List<SeedExercise> = listOf(
        seed("c468cc5d-6e4d-3f7f-8dbf-0936a13591d5", "Bench Press (Barbell)", M.CHEST, E.BARBELL, T.STRENGTH),
        seed("05c0df11-3193-3ebe-b197-f6fa2dc312ed", "Bench Press (Dumbbell)", M.CHEST, E.DUMBBELL, T.STRENGTH),
        seed("716701c7-675b-3499-bd39-b22ff8b6275d", "Bench Press (Smith Machine)", M.CHEST, E.MACHINE, T.STRENGTH),
        seed("8a7571b6-854c-3309-845a-8e524751470b", "Incline Bench Press (Barbell)", M.CHEST, E.BARBELL, T.STRENGTH),
        seed("a071f413-5b62-3f1a-b20d-8ff78dddfeb6", "Incline Bench Press (Dumbbell)", M.CHEST, E.DUMBBELL, T.STRENGTH),
        seed("a1da9831-f7d2-33de-96f1-b2259f62ac8c", "Decline Bench Press (Barbell)", M.CHEST, E.BARBELL, T.STRENGTH),
        seed("6cfd64bd-943e-323d-872f-8acc008b8f03", "Chest Press (Machine)", M.CHEST, E.MACHINE, T.STRENGTH),
        seed("0b6cce3f-b8ad-34e0-b3ff-1a9ccdd636a3", "Chest Fly (Dumbbell)", M.CHEST, E.DUMBBELL, T.STRENGTH),
        seed("e1aa5e32-034c-31bf-b9ef-b96fc9058dd7", "Incline Chest Fly (Dumbbell)", M.CHEST, E.DUMBBELL, T.STRENGTH),
        seed("afac87b5-cd2a-3440-83af-e7f550ac252b", "Cable Crossover", M.CHEST, E.CABLE, T.STRENGTH),
        seed("c4bee094-501a-37ff-9bc6-639414e8954b", "Pec Deck", M.CHEST, E.MACHINE, T.STRENGTH),
        seed("8aa4d428-ad34-3c24-8574-8801ea203aaf", "Push-Up", M.CHEST, E.NONE, T.BODYWEIGHT),
        seed("d2ec3f68-36a1-3f17-b01c-416e300d46ad", "Chest Dip", M.CHEST, E.NONE, T.BODYWEIGHT),

        seed("bb018da4-2448-3af1-a004-47c56e538347", "Deadlift (Barbell)", M.BACK, E.BARBELL, T.STRENGTH),
        seed("6ad2fb0a-fab2-3602-85a6-f337051f79ac", "Rack Pull (Barbell)", M.BACK, E.BARBELL, T.STRENGTH),
        seed("61252d49-e23d-313a-badc-cca9a633d174", "Pull-Up", M.BACK, E.NONE, T.BODYWEIGHT),
        seed("20404c61-9c66-3d94-af19-5b71923210eb", "Chin-Up", M.BACK, E.NONE, T.BODYWEIGHT),
        seed("e6406b28-868c-312b-a838-72ea7e2bbc73", "Lat Pulldown (Cable)", M.BACK, E.CABLE, T.STRENGTH),
        seed("79e927a1-7d36-3f26-bae7-e68ce7ee0d45", "Straight-Arm Pulldown (Cable)", M.BACK, E.CABLE, T.STRENGTH),
        seed("610b0b12-f869-3ffc-a84e-92d90acefed2", "Seated Row (Cable)", M.BACK, E.CABLE, T.STRENGTH),
        seed("760e8af5-17b4-33b5-968f-3a4cb9cb6d28", "Seated Row (Machine)", M.BACK, E.MACHINE, T.STRENGTH),
        seed("b7e3bc2e-1488-3fb3-90b4-104ddd3a04d9", "Bent-Over Row (Barbell)", M.BACK, E.BARBELL, T.STRENGTH),
        seed("6939ce43-07cc-3b99-820d-9b2e1e66af7a", "Pendlay Row (Barbell)", M.BACK, E.BARBELL, T.STRENGTH),
        seed("9221f5a7-594f-3e4f-843d-aa7aff9f722e", "One-Arm Row (Dumbbell)", M.BACK, E.DUMBBELL, T.STRENGTH),
        seed("0e95a344-868e-3a0c-87bd-f2fde94cf4d3", "T-Bar Row", M.BACK, E.BARBELL, T.STRENGTH),
        seed("6d6a4fd2-369e-367a-8d96-1bf03d077928", "Inverted Row", M.BACK, E.NONE, T.BODYWEIGHT),
        seed("3a526bcb-a2f5-39e2-b5e6-42aea0ba80ad", "Back Extension", M.BACK, E.NONE, T.BODYWEIGHT),
        seed("0a9dfc3d-126b-3c92-91c3-383a6cd61a03", "Shrug (Barbell)", M.BACK, E.BARBELL, T.STRENGTH),
        seed("85f58bb3-5b9b-31cd-b55a-ecc7c8918116", "Shrug (Dumbbell)", M.BACK, E.DUMBBELL, T.STRENGTH),

        seed("cfa18498-e98e-321e-ab8c-82fb0afcaa4e", "Overhead Press (Barbell)", M.SHOULDERS, E.BARBELL, T.STRENGTH),
        seed("2cbb458f-6eb3-3620-ab5b-d0653840ac41", "Push Press (Barbell)", M.SHOULDERS, E.BARBELL, T.STRENGTH),
        seed("3eaa63b4-7345-3997-9ff5-1deac01a50d0", "Shoulder Press (Dumbbell)", M.SHOULDERS, E.DUMBBELL, T.STRENGTH),
        seed("b8568c80-fe55-32cd-b46d-dde8e1c84349", "Shoulder Press (Machine)", M.SHOULDERS, E.MACHINE, T.STRENGTH),
        seed("0e87e853-1c7b-373e-98af-179fdf24d3ec", "Arnold Press (Dumbbell)", M.SHOULDERS, E.DUMBBELL, T.STRENGTH),
        seed("fd65a9ed-a57f-3358-ba00-6be74ab76e75", "Landmine Press", M.SHOULDERS, E.BARBELL, T.STRENGTH),
        seed("044de46a-5938-370f-9748-7610d946fa24", "Lateral Raise (Dumbbell)", M.SHOULDERS, E.DUMBBELL, T.STRENGTH),
        seed("fe5dc4de-7868-3d76-93d6-8a4a489db066", "Lateral Raise (Cable)", M.SHOULDERS, E.CABLE, T.STRENGTH),
        seed("144a937b-c20a-3172-adf0-e70f217f37e3", "Front Raise (Dumbbell)", M.SHOULDERS, E.DUMBBELL, T.STRENGTH),
        seed("9b708767-bd19-3508-9032-85b2254fd9c1", "Rear Delt Fly (Dumbbell)", M.SHOULDERS, E.DUMBBELL, T.STRENGTH),
        seed("745194a5-5fad-3bbb-bff0-98b63da66d74", "Rear Delt Fly (Machine)", M.SHOULDERS, E.MACHINE, T.STRENGTH),
        seed("5d7f8669-8195-3143-92a2-0da537a000e6", "Face Pull (Cable)", M.SHOULDERS, E.CABLE, T.STRENGTH),
        seed("449aa287-871c-3029-b66c-1c9e9e2dd866", "Upright Row (Barbell)", M.SHOULDERS, E.BARBELL, T.STRENGTH),
        seed("6c435ba8-7626-3e43-9cc1-15d46b72d31b", "Band Pull-Apart", M.SHOULDERS, E.BAND, T.BODYWEIGHT),
        seed("d98e77e7-33f0-3e09-833d-e8ce1185cb6c", "Pike Push-Up", M.SHOULDERS, E.NONE, T.BODYWEIGHT),

        seed("ed07d5e2-d025-3a71-82c9-5451e70ab7db", "Bicep Curl (Barbell)", M.BICEPS, E.BARBELL, T.STRENGTH),
        seed("30da761c-316a-3b0d-b2a1-5c06943f482c", "Bicep Curl (Dumbbell)", M.BICEPS, E.DUMBBELL, T.STRENGTH),
        seed("5a7b578b-00a9-3ca8-869a-c4cfa56add52", "Bicep Curl (Cable)", M.BICEPS, E.CABLE, T.STRENGTH),
        seed("8f78b216-5687-31bd-86f1-b66d195a7531", "Bicep Curl (Band)", M.BICEPS, E.BAND, T.BODYWEIGHT),
        seed("e1fbdde0-0518-3a22-b4d1-cb8b80a79f38", "EZ-Bar Curl", M.BICEPS, E.BARBELL, T.STRENGTH),
        seed("31bfabb3-8141-3327-be5f-d0cf95c90df1", "Hammer Curl (Dumbbell)", M.BICEPS, E.DUMBBELL, T.STRENGTH),
        seed("599fe36b-95ad-313b-a125-b46228453773", "Incline Curl (Dumbbell)", M.BICEPS, E.DUMBBELL, T.STRENGTH),
        seed("966c915a-217b-3434-b77b-406979e731e5", "Preacher Curl (Machine)", M.BICEPS, E.MACHINE, T.STRENGTH),
        seed("7b25b711-5f5e-397d-9f91-9a4c783029c2", "Concentration Curl (Dumbbell)", M.BICEPS, E.DUMBBELL, T.STRENGTH),

        seed("8b703fee-f2c4-3a3f-a490-c4ea5ebfd2a2", "Triceps Pushdown (Cable)", M.TRICEPS, E.CABLE, T.STRENGTH),
        seed("029613c4-04b9-3e9b-891f-380b792d5f1b", "Overhead Triceps Extension (Cable)", M.TRICEPS, E.CABLE, T.STRENGTH),
        seed("82a0730a-e000-3605-b733-7afa694cfd4b", "Overhead Triceps Extension (Dumbbell)", M.TRICEPS, E.DUMBBELL, T.STRENGTH),
        seed("84f872d7-060d-30e0-b5a8-bfc9db9a5f21", "Skull Crusher (Barbell)", M.TRICEPS, E.BARBELL, T.STRENGTH),
        seed("4a0a61f1-6d6f-3b8b-85b9-92e470b4ab4c", "Close-Grip Bench Press (Barbell)", M.TRICEPS, E.BARBELL, T.STRENGTH),
        seed("508a6942-fab0-3e38-bda4-986e4db2b43b", "Triceps Kickback (Dumbbell)", M.TRICEPS, E.DUMBBELL, T.STRENGTH),
        seed("894f6bba-3f6f-3eec-9040-0e5ae685b302", "Triceps Dip", M.TRICEPS, E.NONE, T.BODYWEIGHT),
        seed("06903318-8e0a-3f97-a0e0-72127983fee2", "Bench Dip", M.TRICEPS, E.NONE, T.BODYWEIGHT),
        seed("76ab8192-3eca-3407-882f-7d7c9c2d5319", "Diamond Push-Up", M.TRICEPS, E.NONE, T.BODYWEIGHT),

        seed("8c8e08e8-f575-3ce8-a880-f6b7de789e37", "Wrist Curl (Barbell)", M.FOREARMS, E.BARBELL, T.STRENGTH),
        seed("389eb1bf-1c4d-3761-87e6-253627a28623", "Reverse Curl (Barbell)", M.FOREARMS, E.BARBELL, T.STRENGTH),
        seed("03891879-e478-3b9a-b99a-587d3f4ddc93", "Dead Hang", M.FOREARMS, E.NONE, T.CARDIO),

        seed("93505f26-f8f8-36c7-a4e2-82dd284afdc4", "Crunch", M.CORE, E.NONE, T.BODYWEIGHT),
        seed("2e0e0ac1-c370-3da6-ad9d-3d30d347dc99", "Sit-Up", M.CORE, E.NONE, T.BODYWEIGHT),
        seed("cda6dd17-4b9f-34f2-baa9-72ade5be7a17", "Cable Crunch", M.CORE, E.CABLE, T.STRENGTH),
        seed("6aff8268-aeb4-3ea5-ab2b-ca6b5604f5c5", "Hanging Leg Raise", M.CORE, E.NONE, T.BODYWEIGHT),
        seed("89138ffe-f78e-3c44-9d32-55cd5057bc53", "Hanging Knee Raise", M.CORE, E.NONE, T.BODYWEIGHT),
        seed("3b919946-1224-3e8a-a786-0bbddb38dd48", "Lying Leg Raise", M.CORE, E.NONE, T.BODYWEIGHT),
        seed("ad0e5322-ada0-33bf-80d4-72d976d93369", "Ab Wheel Rollout", M.CORE, E.OTHER, T.BODYWEIGHT),
        seed("21a3801a-1610-39f1-bc87-d9ec35eb9118", "Russian Twist", M.CORE, E.NONE, T.BODYWEIGHT),
        seed("81ca8269-b9b5-3439-a7b7-d9f86261573d", "Pallof Press (Cable)", M.CORE, E.CABLE, T.STRENGTH),
        seed("6d74b039-34e8-33ac-8ec4-b8ce2744725d", "Woodchop (Cable)", M.CORE, E.CABLE, T.STRENGTH),
        seed("3ff4f0b3-1e43-3da4-8a3c-f1f1319e8645", "Plank", M.CORE, E.NONE, T.CARDIO),
        seed("633f183f-7371-3311-bb9b-8017422a3c7c", "Side Plank", M.CORE, E.NONE, T.CARDIO),

        seed("e0b0075f-3eec-3308-aa86-37e8dd76fd34", "Squat (Barbell)", M.QUADS, E.BARBELL, T.STRENGTH),
        seed("183a2cfd-a7d9-3b10-86d2-e7af2c360e38", "Front Squat (Barbell)", M.QUADS, E.BARBELL, T.STRENGTH),
        seed("06fd4b07-bf27-366e-824d-e151969170e0", "Squat (Smith Machine)", M.QUADS, E.MACHINE, T.STRENGTH),
        seed("fa68fa3b-fc66-34da-8208-cf59e441b067", "Goblet Squat (Dumbbell)", M.QUADS, E.DUMBBELL, T.STRENGTH),
        seed("25f4cfe5-6320-35b9-acc4-c2e005b12fba", "Goblet Squat (Kettlebell)", M.QUADS, E.KETTLEBELL, T.STRENGTH),
        seed("8bb73aad-57e6-35be-931b-97384e5f662f", "Hack Squat (Machine)", M.QUADS, E.MACHINE, T.STRENGTH),
        seed("cb154140-d437-331e-9e52-3600a7608c04", "Leg Press (Machine)", M.QUADS, E.MACHINE, T.STRENGTH),
        seed("e570a4b4-0e0b-309e-96fa-5743f246b534", "Leg Extension (Machine)", M.QUADS, E.MACHINE, T.STRENGTH),
        seed("342f5b56-7052-36a1-80b3-fa707cdd2614", "Bulgarian Split Squat (Dumbbell)", M.QUADS, E.DUMBBELL, T.STRENGTH),
        seed("39721931-a572-359c-8647-db07d44d3cc4", "Lunge (Dumbbell)", M.QUADS, E.DUMBBELL, T.STRENGTH),
        seed("9fa31053-64c1-3192-8e24-f802f199dd1e", "Walking Lunge (Dumbbell)", M.QUADS, E.DUMBBELL, T.STRENGTH),
        seed("267fce52-06e2-3899-8e9b-5445ef0b31ff", "Step-Up (Dumbbell)", M.QUADS, E.DUMBBELL, T.STRENGTH),
        seed("9b144f8f-b584-32af-bac3-f95cb7e5c64f", "Bodyweight Squat", M.QUADS, E.NONE, T.BODYWEIGHT),
        seed("675e8565-0f5e-351e-bf15-5ba9f752a0c9", "Wall Sit", M.QUADS, E.NONE, T.CARDIO),

        seed("aedb85ee-9540-3282-93a5-28da5c41c4df", "Romanian Deadlift (Barbell)", M.HAMSTRINGS, E.BARBELL, T.STRENGTH),
        seed("2329531b-217d-3c64-8932-873685dae8da", "Romanian Deadlift (Dumbbell)", M.HAMSTRINGS, E.DUMBBELL, T.STRENGTH),
        seed("0d40a6d2-547a-3703-9d7f-e4618f52d94d", "Stiff-Leg Deadlift (Barbell)", M.HAMSTRINGS, E.BARBELL, T.STRENGTH),
        seed("466105a2-19a3-3273-b112-64257c05e135", "Good Morning (Barbell)", M.HAMSTRINGS, E.BARBELL, T.STRENGTH),
        seed("7795f27b-3516-3c28-a701-8169b95e6648", "Lying Leg Curl (Machine)", M.HAMSTRINGS, E.MACHINE, T.STRENGTH),
        seed("8ade1cfe-5358-3007-aeb3-71bfbf5e1ec9", "Seated Leg Curl (Machine)", M.HAMSTRINGS, E.MACHINE, T.STRENGTH),
        seed("22d8f0d6-d582-33cd-bcbf-35ed1ae45bed", "Nordic Hamstring Curl", M.HAMSTRINGS, E.NONE, T.BODYWEIGHT),

        seed("6a00ad81-d30b-3fea-b886-44e9eba5dddd", "Hip Thrust (Barbell)", M.GLUTES, E.BARBELL, T.STRENGTH),
        seed("63924830-9022-33af-892b-372e02e0e1aa", "Hip Thrust (Machine)", M.GLUTES, E.MACHINE, T.STRENGTH),
        seed("35d3cafc-e0a3-3753-9016-94d0e9d99f97", "Glute Bridge", M.GLUTES, E.NONE, T.BODYWEIGHT),
        seed("3c942672-5df2-3c5a-95de-32e4bc4b0442", "Sumo Deadlift (Barbell)", M.GLUTES, E.BARBELL, T.STRENGTH),
        seed("657fc206-a782-3e10-bbae-850ddaeb942b", "Glute Kickback (Cable)", M.GLUTES, E.CABLE, T.STRENGTH),
        seed("1604f4cf-bcd3-3941-b828-344e9bbb66e5", "Hip Abduction (Machine)", M.GLUTES, E.MACHINE, T.STRENGTH),
        seed("34cecfd7-400c-3a6e-930f-2890f7c6b8e8", "Cable Pull-Through", M.GLUTES, E.CABLE, T.STRENGTH),
        seed("3626b60e-5141-39a5-8bb3-44acfb251920", "Kettlebell Swing", M.GLUTES, E.KETTLEBELL, T.STRENGTH),
        seed("dce59969-d930-3bf4-bc43-b5d024f95bb3", "Lateral Band Walk", M.GLUTES, E.BAND, T.BODYWEIGHT),

        seed("b5fc8cf5-74f3-3ebd-80f4-3e5185cebfef", "Standing Calf Raise (Machine)", M.CALVES, E.MACHINE, T.STRENGTH),
        seed("2b319be9-8021-3aff-a98b-3997df904da7", "Seated Calf Raise (Machine)", M.CALVES, E.MACHINE, T.STRENGTH),
        seed("95e41b18-291c-3a9e-ae80-7ef531921e0e", "Standing Calf Raise (Dumbbell)", M.CALVES, E.DUMBBELL, T.STRENGTH),

        seed("5fb34790-f489-34ca-93c3-dc5f05863f51", "Power Clean (Barbell)", M.FULL_BODY, E.BARBELL, T.STRENGTH),
        seed("2a88c9ea-eeb4-3fb2-a815-258ce90ca8f4", "Clean and Jerk (Barbell)", M.FULL_BODY, E.BARBELL, T.STRENGTH),
        seed("0358e1dd-2d0a-38c8-ac2a-822f588c6a86", "Snatch (Barbell)", M.FULL_BODY, E.BARBELL, T.STRENGTH),
        seed("f44fb8a7-c379-3ade-a4cd-2551df7967f9", "Thruster (Barbell)", M.FULL_BODY, E.BARBELL, T.STRENGTH),
        seed("96ce4adc-8cf2-3ed8-8f73-462f1abfe6e6", "Turkish Get-Up (Kettlebell)", M.FULL_BODY, E.KETTLEBELL, T.STRENGTH),
        seed("068fce5f-b19e-3c8c-ae70-76674b5fb49f", "Wall Ball", M.FULL_BODY, E.OTHER, T.STRENGTH),
        seed("7534e26d-e425-3b76-ac1a-05974e0deb00", "Burpee", M.FULL_BODY, E.NONE, T.BODYWEIGHT),

        seed("eceb445b-d692-38a6-bd0a-d40234d6538a", "Running", M.CARDIO, E.NONE, T.CARDIO),
        seed("a6b5446b-50aa-39bc-9f3c-22a451d4bbac", "Running (Treadmill)", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("c2edd03d-1d00-332b-a46a-758d84286a38", "Walking", M.CARDIO, E.NONE, T.CARDIO),
        seed("83b6c57c-2eb3-35e7-9261-358e4ed75b41", "Cycling", M.CARDIO, E.OTHER, T.CARDIO),
        seed("34b3134e-c38c-3db2-b0c4-91504debb0d6", "Stationary Bike", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("08d1e91d-c4ec-3ff2-9305-bd28925cf8eb", "Air Bike", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("4a300d9d-e11e-31e5-9350-a00cbba6b629", "Rowing (Machine)", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("4c69b72b-1494-3e7f-91dd-d88707201079", "Ski Erg", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("f3a0ffe9-ce06-3b9b-9ed9-2af69868d80c", "Elliptical", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("bcc9b3a0-702d-357e-974f-6f4806bdebe7", "Stair Climber", M.CARDIO, E.CARDIO_MACHINE, T.CARDIO),
        seed("ebc319e2-fcbf-392d-82c8-3e0ac2237683", "Jump Rope", M.CARDIO, E.OTHER, T.CARDIO),
        seed("7ec91ba5-f11b-36a0-97e8-c7a8aa079306", "Swimming", M.CARDIO, E.NONE, T.CARDIO),
    )

    /** Inserts the library on the database's creation transaction, before anything can read it. */
    fun insertInto(db: SupportSQLiteDatabase, createdAt: Instant) {
        db.compileStatement(INSERT).use { statement ->
            for (exercise in exercises) {
                statement.bindString(1, exercise.id)
                statement.bindString(2, exercise.name)
                statement.bindString(3, exercise.primaryMuscle.name)
                statement.bindString(4, exercise.equipment.name)
                statement.bindString(5, exercise.type.name)
                statement.bindLong(6, createdAt.toEpochMilli())
                statement.executeInsert()
            }
        }
    }

    // Column names must match ExerciseEntity; LiftBookDatabaseTest reads the seed back through
    // the DAO to catch any drift.
    private const val INSERT =
        "INSERT OR IGNORE INTO exercises " +
            "(id, name, primaryMuscle, equipment, type, isCustom, isArchived, defaultRestSeconds, notes, createdAt) " +
            "VALUES (?, ?, ?, ?, ?, 0, 0, NULL, NULL, ?)"
}

internal data class SeedExercise(
    val id: String,
    val name: String,
    val primaryMuscle: M,
    val equipment: E,
    val type: T,
)

private fun seed(id: String, name: String, primaryMuscle: M, equipment: E, type: T) =
    SeedExercise(id, name, primaryMuscle, equipment, type)
