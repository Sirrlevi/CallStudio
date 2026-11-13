package com.callstudio.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The token problems the user can actually fix must each get their own, specific message. */
class UpdateErrorMessageTest {
    @Test fun invalidTokenIsSaidPlainly() {
        val message = updateErrorMessage(401, Stage.REPOSITORY, "Bad credentials")
        assertTrue(message.contains("isn't valid"))
        assertTrue(message.contains("Bad credentials"))
    }

    @Test fun repositoryNotVisibleExplainsTheTokenSetup() {
        val message = updateErrorMessage(404, Stage.REPOSITORY, "Not Found")
        assertTrue(message.contains("Only select repositories"))
        assertTrue(message.contains("CallStudio"))
    }

    @Test fun missingReleaseIsNotBlamedOnTheToken() {
        val message = updateErrorMessage(404, Stage.RELEASE)
        assertTrue(message.contains("no \"debug-latest\" release"))
        assertFalse(message.contains("token can't see"))
    }

    @Test fun missingContentsPermissionIsNamed() {
        val message = updateErrorMessage(403, Stage.RELEASE, "Resource not accessible by personal access token")
        assertTrue(message.contains("Contents"))
        assertTrue(message.contains("Read-only"))
    }

    @Test fun rateLimitIsNotBlamedOnPermissions() {
        val message = updateErrorMessage(403, Stage.RELEASE, "API rate limit exceeded")
        assertTrue(message.contains("limit"))
        assertFalse(message.contains("Contents"))
    }

    @Test fun otherStatusesKeepTheCode() {
        assertTrue(updateErrorMessage(502, Stage.FILE).contains("502"))
    }
}
