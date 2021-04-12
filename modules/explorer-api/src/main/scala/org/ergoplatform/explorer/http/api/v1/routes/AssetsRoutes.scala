package org.ergoplatform.explorer.http.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.semigroupk._
import io.chrisdavenport.log4cats.Logger
import org.ergoplatform.explorer.http.api.ApiErr
import org.ergoplatform.explorer.http.api.algebra.AdaptThrowable.AdaptThrowableEitherT
import org.ergoplatform.explorer.http.api.syntax.adaptThrowable._
import org.ergoplatform.explorer.http.api.v1.defs.AssetsEndpointDefs
import org.ergoplatform.explorer.http.api.v1.services.{Assets, Tokens}
import org.ergoplatform.explorer.settings.RequestsSettings
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s._

final class AssetsRoutes[
  F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], ApiErr]
](settings: RequestsSettings, assets: Assets[F], tokens: Tokens[F])(implicit opts: Http4sServerOptions[F, F]) {

  val defs = new AssetsEndpointDefs(settings)

  val routes: HttpRoutes[F] =
    listTokensR <+> searchByTokenIdR

  private def searchByTokenIdR: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(defs.searchByTokenIdDef) { case (q, paging) =>
      assets.getAllLike(q, paging).adaptThrowable.value
    }

  private def listTokensR: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(defs.listTokensDef) { case (paging, ordering) =>
      tokens.getAll(paging, ordering).adaptThrowable.value
    }
}

object AssetsRoutes {

  def apply[F[_]: Concurrent: ContextShift: Timer: Logger](
    settings: RequestsSettings,
    assets: Assets[F],
    tokens: Tokens[F]
  )(implicit opts: Http4sServerOptions[F, F]): HttpRoutes[F] =
    new AssetsRoutes(settings, assets, tokens).routes
}
