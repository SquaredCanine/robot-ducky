package furhatos.app.newskill.nlu

import furhatos.nlu.Intent
import furhatos.util.Language

/**
 * The user telling the duck to settle down.
 *
 * WORKSHOP BLANK — this ships with one example so the project compiles. One example is not enough
 * for the recogniser to match reliably, so filling this in is the first task.
 *
 * Things worth thinking about while you do:
 * - People say this many ways, and rarely the polite way. Cover the impatient phrasings.
 * - [lang] is handed to you for a reason. The duck answers in whatever language it was spoken to,
 *   so it has to be *told to shut up* in those languages too — see how [furhatos.nlu.common.Yes]
 *   branches on [Language] in the SDK if you want a pattern to copy.
 * - Keep examples short and spoken. Speech recognition gives you no punctuation and no capitals.
 */
class CalmDown : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            // TODO: how does a user actually tell a robot to settle down? Add more phrasings,
            //  and handle at least one language besides English.
            "calm down"
        )
    }
}
