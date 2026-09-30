package furhatos.app.newskill.nlu

import furhatos.nlu.Intent
import furhatos.util.Language

/**
 * The user telling the duck to settle down.
*/
class CalmDown : Intent() {
    override fun getExamples(lang: Language): List<String> {
        return listOf(
            // TODO: how does a user actually tell a robot to settle down? Add more phrasings,
            "something i will never ever ever say ever ever ever you can replace this sentence and add more once you reach the relevant exercise"
        )
    }
}
